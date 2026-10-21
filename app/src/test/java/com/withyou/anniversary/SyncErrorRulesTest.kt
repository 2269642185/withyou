package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test

class SyncErrorRulesTest {
    @Test fun onlyConfirmedRefreshFailureRequestsLogin() {
        assertTrue(needsReauthentication(400,"invalid_grant",SyncStage.SESSION))
        assertTrue(needsReauthentication(400,"refresh_token无效或已过期",SyncStage.SESSION))
        assertFalse(needsReauthentication(400,"invalid_client",SyncStage.SESSION))
        assertFalse(needsReauthentication(400,"23514",SyncStage.WRITE_TASK))
        assertFalse(needsReauthentication(503,"",SyncStage.SESSION))
        assertFalse(needsReauthentication(0,"TIMEOUT",SyncStage.SESSION))
    }
    @Test fun wrongPasswordAndSudoFailureAreNotSessionExpiry() {
        assertFalse(needsReauthentication(401,"invalid_password",SyncStage.LOGIN))
        assertFalse(needsReauthentication(401,"invalid_password",SyncStage.AUTH_ACTION))
        assertTrue(needsReauthentication(401,"JWT expired",SyncStage.READ))
    }
}
