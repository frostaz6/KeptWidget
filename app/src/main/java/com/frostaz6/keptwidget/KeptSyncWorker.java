package com.frostaz6.keptwidget;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Resumes queued edits once Android reports an available network. */
public final class KeptSyncWorker extends Worker {
    public KeptSyncWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        if(SecureStore.token(c).isEmpty())return Result.success();
        try{
            return PendingSync.flush(c)?Result.success():Result.retry();
        }catch(Exception error){
            return Result.retry();
        }
    }
}
