package com.frostaz6.keptwidget;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conservative encrypted offline outbox.
 *
 * Server capability noteRevisions=false: never replay an old whole-note snapshot.
 * Recheck every changed field before PATCH and stop on a concurrent edit.
 * WorkManager retries network failures; conflicts remain for explicit user review.
 */
final class PendingSync {
    private static final Object LOCK=new Object();
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    private PendingSync(){}

    static void schedule(Context c) {
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(KeptSyncWorker.class)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(
            "keptwidget-offline-sync",ExistingWorkPolicy.APPEND_OR_REPLACE,work);
    }

    static JSONArray entries(Context c) {
        synchronized(LOCK) {
            try {
                String raw=SecureStore.readPending(c);
                return raw.isBlank()?new JSONArray():new JSONArray(raw);
            } catch(Exception ignored) { return new JSONArray(); }
        }
    }

    private static void save(Context c,JSONArray array) {
        SecureStore.savePending(c,array.toString());
    }

    static int count(Context c) { return entries(c).length(); }
    static int conflicts(Context c) {
        JSONArray a=entries(c);
        int count=0;
        for(int i=0;i<a.length();i++)if("conflict".equals(a.optJSONObject(i).optString("state")))count++;
        return count;
    }

    static void create(Context c,JSONObject payload) throws JSONException {
        synchronized(LOCK) {
            JSONArray a=entries(c);
            if(a.length()>=200)throw new IllegalStateException("Offline queue full (200 items).");
            JSONObject op=new JSONObject().put("id",UUID.randomUUID().toString())
                .put("kind","create").put("note",new JSONObject(payload.toString()))
                .put("state","queued").put("createdAt",System.currentTimeMillis());
            a.put(op);save(c,a);
        }
        schedule(c);
    }

    static void edit(Context c,long noteId,JSONObject changes,JSONObject original) throws JSONException {
        if(changes.length()==0)return;
        synchronized(LOCK) {
            JSONArray a=entries(c);
            JSONObject pending=null;
            for(int i=0;i<a.length();i++){
                JSONObject entry=a.optJSONObject(i);
                if(entry!=null && "edit".equals(entry.optString("kind"))
                    && entry.optLong("noteId")==noteId && "queued".equals(entry.optString("state"))){
                    pending=entry;break;
                }
            }
            if(pending==null) {
                if(a.length()>=200)throw new IllegalStateException("Offline queue full (200 items).");
                pending=new JSONObject().put("id",UUID.randomUUID().toString())
                    .put("kind","edit").put("noteId",noteId).put("state","queued")
                    .put("fields",new JSONObject()).put("baseline",new JSONObject());
                a.put(pending);
            }
            JSONObject fields=pending.getJSONObject("fields");
            JSONObject baseline=pending.getJSONObject("baseline");
            java.util.Iterator<String> keys=changes.keys();
            while(keys.hasNext()){
                String key=keys.next();
                if(!baseline.has(key)) baseline.put(key,original.has(key)?original.get(key):JSONObject.NULL);
                fields.put(key,changes.get(key));
            }
            save(c,a);
        }
        schedule(c);
    }

    static boolean checkbox(Context c,long noteId,long itemId) throws JSONException {
        synchronized(LOCK) {
            JSONArray a=entries(c);
            JSONObject existing=null;
            for(int i=0;i<a.length();i++){
                JSONObject e=a.optJSONObject(i);
                if(e!=null && "check".equals(e.optString("kind"))
                   && e.optLong("noteId")==noteId && e.optLong("itemId")==itemId
                   && "queued".equals(e.optString("state"))){existing=e;break;}
            }
            if(existing!=null){
                boolean next=!existing.optBoolean("desired");
                if(existing.has("baselineDone") && next==existing.optBoolean("baselineDone")) {
                    // Two offline taps cancel out instead of creating a needless mutation.
                    a.remove(indexOf(a,existing.optString("id")));
                } else {
                    existing.put("desired",next);
                }
                save(c,a);
                schedule(c);
                return true;
            }
            JSONObject note=cachedNote(c,noteId);
            if(note==null)return false;
            JSONArray list=note.optJSONArray("checkBoxes");
            if(list==null)return false;
            for(int i=0;i<list.length();i++){
                JSONObject item=list.optJSONObject(i);
                if(item!=null && item.optLong("id",-1)==itemId){
                    if(a.length()>=200)throw new IllegalStateException("Offline queue full (200 items).");
                    a.put(new JSONObject().put("id",UUID.randomUUID().toString())
                        .put("kind","check").put("state","queued")
                        .put("noteId",noteId).put("itemId",itemId)
                        .put("baselineDone",item.optBoolean("done"))
                        .put("desired",!item.optBoolean("done")));
                    save(c,a);
                    schedule(c);
                    return true;
                }
            }
            return false;
        }
    }

    private static int indexOf(JSONArray entries,String operationId) {
        for(int i=0;i<entries.length();i++){
            JSONObject e=entries.optJSONObject(i);
            if(e!=null && operationId.equals(e.optString("id")))return i;
        }
        return -1;
    }

    static JSONObject cachedNote(Context c,long id) {
        try {
            String raw=SecureStore.singleNote(c,id);
            if(!raw.isBlank())return new JSONObject(raw);
            JSONArray notes=new JSONArray(SecureStore.cachedNotes(c));
            for(int i=0;i<notes.length();i++){
                JSONObject n=notes.optJSONObject(i);
                if(n!=null && n.optLong("id",-1)==id)return n;
            }
        }catch(Exception ignored){}
        return null;
    }

    /** Apply local changes to a display snapshot without ever overwriting remote data. */
    static JSONArray overlay(Context c,JSONArray remote) {
        JSONArray a=entries(c);
        JSONArray output=new JSONArray();
        try {
            for(int i=0;i<remote.length();i++){
                JSONObject original=remote.optJSONObject(i);
                if(original==null)continue;
                JSONObject note=new JSONObject(original.toString());
                for(int j=0;j<a.length();j++){
                    JSONObject e=a.optJSONObject(j);
                    if(e==null || "create".equals(e.optString("kind"))
                       || e.optLong("noteId",-1)!=note.optLong("id",-2))continue;
                    if("edit".equals(e.optString("kind"))){
                        JSONObject fields=e.optJSONObject("fields");
                        if(fields!=null){
                            java.util.Iterator<String> keys=fields.keys();
                            while(keys.hasNext()){String k=keys.next();note.put(k,fields.get(k));}
                        }
                    }else if("check".equals(e.optString("kind"))){
                        JSONArray boxes=note.optJSONArray("checkBoxes");
                        for(int k=0;boxes!=null&&k<boxes.length();k++){
                            JSONObject b=boxes.optJSONObject(k);
                            if(b!=null&&b.optLong("id",-1)==e.optLong("itemId"))
                                b.put("done",e.optBoolean("desired"));
                        }
                    }
                    note.put("syncPending",true);
                    if("conflict".equals(e.optString("state")))note.put("syncConflict",true);
                }
                output.put(note);
            }
            for(int j=0;j<a.length();j++){
                JSONObject e=a.optJSONObject(j);
                if(e==null||!"create".equals(e.optString("kind")))continue;
                JSONObject n=new JSONObject(e.getJSONObject("note").toString());
                String syncId=n.optString("syncId","");
                boolean exists=false;
                for(int i=0;i<output.length();i++){
                    JSONObject current=output.optJSONObject(i);
                    if(current!=null&&!syncId.isEmpty()&&syncId.equals(current.optString("syncId")))
                        exists=true;
                }
                if(!exists){
                    n.put("id",-(Math.abs(e.optString("id").hashCode())+1L));
                    n.put("syncPending",true);
                    output.put(n);
                }
            }
        } catch(Exception ignored){ return remote; }
        return output;
    }

    private static void removeEntry(Context c,String operationId) {
        synchronized(LOCK){
            JSONArray original=entries(c),remaining=new JSONArray();
            for(int i=0;i<original.length();i++){
                JSONObject op=original.optJSONObject(i);
                if(op!=null&&!operationId.equals(op.optString("id")))remaining.put(op);
            }
            save(c,remaining);
        }
    }

    static void discard(Context c,String operationId) {
        removeEntry(c,operationId);
        CollectionWidgetProvider.refreshAll(c);
    }

    private static void markConflict(Context c,String opId,String reason) {
        synchronized(LOCK){
            JSONArray a=entries(c);
            for(int i=0;i<a.length();i++){
                JSONObject op=a.optJSONObject(i);
                if(op!=null&&opId.equals(op.optString("id"))){
                    try{op.put("state","conflict");op.put("error",reason);}catch(Exception ignored){}
                    break;
                }
            }
            save(c,a);
        }
    }

    static boolean equivalent(Object a,Object b){
        if(a==JSONObject.NULL)a=null;
        if(b==JSONObject.NULL)b=null;
        if(a==null||b==null)return a==b;
        if(a instanceof JSONObject && b instanceof JSONObject){
            JSONObject x=(JSONObject)a,y=(JSONObject)b;
            if(x.length()!=y.length())return false;
            java.util.Iterator<String> keys=x.keys();
            while(keys.hasNext()){
                String k=keys.next();
                if(!y.has(k)||!equivalent(x.opt(k),y.opt(k)))return false;
            }
            return true;
        }
        if(a instanceof JSONArray && b instanceof JSONArray){
            JSONArray x=(JSONArray)a,y=(JSONArray)b;
            if(x.length()!=y.length())return false;
            for(int i=0;i<x.length();i++)if(!equivalent(x.opt(i),y.opt(i)))return false;
            return true;
        }
        return Objects.equals(a,b);
    }

    private static void applyOne(Context c,JSONObject op) throws Exception {
        String kind=op.optString("kind");
        if("create".equals(kind)){
            JSONObject note=op.getJSONObject("note");
            String syncId=note.optString("syncId");
            JSONArray server=KeptApi.allNotes(c);
            for(int i=0;i<server.length();i++){
                JSONObject current=server.optJSONObject(i);
                if(current!=null&&!syncId.isEmpty()&&syncId.equals(current.optString("syncId")))
                    return; // Previous request may have succeeded before its response was lost.
            }
            KeptApi.add(c,note);
            return;
        }
        long noteId=op.getLong("noteId");
        JSONObject current=KeptApi.note(c,noteId);
        if("edit".equals(kind)){
            JSONObject baseline=op.getJSONObject("baseline");
            JSONObject fields=op.getJSONObject("fields");
            java.util.Iterator<String> keys=fields.keys();
            while(keys.hasNext()){
                String key=keys.next();
                if(!equivalent(current.opt(key),baseline.opt(key))
                    && !equivalent(current.opt(key),fields.opt(key))){
                    throw new Conflict("Server changed "+key+" while you were offline.");
                }
            }
            JSONObject toApply=new JSONObject();
            keys=fields.keys();
            while(keys.hasNext()){
                String key=keys.next();
                if(!equivalent(current.opt(key),fields.opt(key)))toApply.put(key,fields.get(key));
            }
            if(toApply.length()>0)KeptApi.patch(c,noteId,toApply);
            return;
        }
        if("check".equals(kind)){
            // A legacy operation without baselineDone can only be accepted if
            // the server already matches the intended result.
            Boolean baseline=op.has("baselineDone") ?
                op.optBoolean("baselineDone") : null;
            ChecklistMutation.Decision plan=ChecklistMutation.apply(
                current.optJSONArray("checkBoxes"),op.getLong("itemId"),baseline,
                op.optBoolean("desired"));
            if(plan.isConflict())throw new Conflict(plan.conflict);
            if(plan.needsWrite) {
                KeptApi.patch(c,noteId,new JSONObject().put("checkBoxes",plan.items));
            }
        }
    }

    private static final class Conflict extends Exception {
        Conflict(String message){super(message);}
    }

    /** Do not confuse transient API failures with a permanent edit conflict. */
    static boolean shouldRetryHttp(int status) {
        return status==408 || status==425 || status==429 || status>=500;
    }

    private static boolean hasQueued(Context c) {
        JSONArray pending=entries(c);
        for(int i=0;i<pending.length();i++){
            JSONObject entry=pending.optJSONObject(i);
            if(entry!=null && "queued".equals(entry.optString("state")))return true;
        }
        return false;
    }

    /** True means all queued work is complete (or awaiting manual conflict review). */
    static boolean flush(Context c) {
        if(!RUNNING.compareAndSet(false,true))return true;
        boolean online=true;
        try{
            // Re-read the queue after every pass so edits enqueued during a sync
            // are not stranded by a stale snapshot. Limit work per invocation.
            for(int pass=0;pass<5 && online;pass++){
                JSONArray snapshot=entries(c);
                boolean attempted=false;
                for(int i=0;i<snapshot.length();i++){
                    JSONObject op=snapshot.optJSONObject(i);
                    if(op==null || !"queued".equals(op.optString("state")))continue;
                    attempted=true;
                    String id=op.optString("id");
                    try{
                        applyOne(c,op);
                        removeEntry(c,id);
                    }catch(Conflict e){
                        markConflict(c,id,e.getMessage());
                    }catch(KeptApi.ApiException e){
                        if(e.status==401 || shouldRetryHttp(e.status)){online=false;break;}
                        markConflict(c,id,"Kept rejected the change (HTTP "+e.status+"): "+e.getMessage());
                    }catch(IOException e){online=false;break;}
                    catch(Exception e){markConflict(c,id,e.getMessage());}
                }
                if(!attempted)break;
            }
        }finally{
            RUNNING.set(false);
            CollectionWidgetProvider.refreshAll(c);
        }
        return online && !hasQueued(c);
    }

    /** A manual review is required for explicit conflicts. Never force-overwrite. */
    static void retryConflict(Context c,String opId){
        synchronized(LOCK){
            JSONArray a=entries(c);
            for(int i=0;i<a.length();i++){
                JSONObject e=a.optJSONObject(i);
                if(e!=null&&opId.equals(e.optString("id"))){
                    try{e.put("state","queued");e.remove("error");}catch(Exception ignored){}
                }
            }
            save(c,a);
        }
        schedule(c);
    }
}
