package com.frostaz6.keptwidget;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** No Kept account or external server is needed for conflict and replay tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public final class ChecklistMutationTest {
    private static JSONArray items() throws Exception {
        return new JSONArray("[{\"id\":10,\"data\":\"Bread\",\"done\":false},"+
            "{\"id\":11,\"data\":\"Milk\",\"done\":true}]");
    }

    @Test public void toggleChangesOnlyRequestedCheckbox() throws Exception {
        JSONArray before=items();
        ChecklistMutation.Decision p=ChecklistMutation.apply(before,10L,false,true);
        assertFalse(p.isConflict());
        assertTrue(p.needsWrite);
        assertTrue(p.items.getJSONObject(0).getBoolean("done"));
        assertTrue(p.items.getJSONObject(1).getBoolean("done"));
        assertFalse("Must not mutate the source snapshot",before.getJSONObject(0).getBoolean("done"));
    }

    @Test public void retriesAreIdempotent() throws Exception {
        JSONArray server=items();
        server.getJSONObject(0).put("done",true);
        ChecklistMutation.Decision p=ChecklistMutation.apply(server,10L,false,true);
        assertFalse(p.isConflict());
        assertFalse("Already saved; do not send a second PATCH",p.needsWrite);
    }

    @Test public void simultaneousRemoteToggleFlagsConflict() throws Exception {
        JSONArray server=items();
        ChecklistMutation.Decision p=ChecklistMutation.apply(server,11L,true,true);
        assertFalse("Already in requested state",p.needsWrite);
        assertFalse(p.isConflict());
        ChecklistMutation.Decision conflict=ChecklistMutation.apply(server,11L,true,false);
        assertFalse("Expected server state matches baseline",conflict.isConflict());
        JSONArray changed=items();
        changed.getJSONObject(1).put("done",false);
        ChecklistMutation.Decision alreadyDone=ChecklistMutation.apply(changed,11L,true,false);
        assertFalse(alreadyDone.isConflict());
        assertFalse(alreadyDone.needsWrite);
    }

    @Test public void legacyOperationsRequireReviewWhenRemoteDiffers() throws Exception {
        ChecklistMutation.Decision conflict=ChecklistMutation.apply(items(),10L,null,true);
        assertTrue(conflict.isConflict());
        ChecklistMutation.Decision matched=ChecklistMutation.apply(items(),11L,null,true);
        assertFalse(matched.isConflict());
        assertFalse(matched.needsWrite);
    }

    @Test public void deletedCheckboxIsConflict() throws Exception {
        ChecklistMutation.Decision p=ChecklistMutation.apply(items(),9999L,false,true);
        assertTrue(p.isConflict());
    }

    @Test public void transientServerResponsesMustRetry() {
        for(int code:new int[]{408,425,429,500,502,503,504})
            assertTrue("Expected retry for HTTP "+code,PendingSync.shouldRetryHttp(code));
        for(int code:new int[]{400,401,403,404,409,423})
            assertFalse("Do not retry HTTP "+code+" as transient",PendingSync.shouldRetryHttp(code));
    }
}
