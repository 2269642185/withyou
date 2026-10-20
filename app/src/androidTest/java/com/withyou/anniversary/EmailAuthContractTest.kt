package com.withyou.anniversary

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailAuthContractTest {
    private val address="friend@example.test"
    @Test fun verifiedExistingMailboxNeverTriggersSignupOrChangesItsPassword()=runBlocking {
        var calls=0
        val client=EmailAuth(call={path,_,_,_->calls++;assertEquals("/auth/v1/verification/verify",path);JSONObject().put("verification_token","verified")},now={0})
        try {client.register(EmailChallenge("id",address,EmailPurpose.REGISTER,1000,0,true),address,"123456","Aa123456!");fail("Must reject existing mailbox")}
        catch(_:IllegalArgumentException) {}
        assertEquals(1,calls)
    }
    @Test fun mismatchedExpiredAndWrongPurposeChallengesNeverReachNetwork()=runBlocking {
        var calls=0
        val client=EmailAuth(call={_,_,_,_->calls++;JSONObject()},now={1000})
        for(challenge in listOf(EmailChallenge("id",address,EmailPurpose.RESET,2000,0),EmailChallenge("id",address,EmailPurpose.REGISTER,999,0),EmailChallenge("id","other@example.test",EmailPurpose.REGISTER,2000,0))) {
            try {client.register(challenge,address,"123456","Aa123456!");fail("Must reject") } catch(_:IllegalArgumentException) {}
        }
        assertEquals(0,calls)
    }
    @Test fun registrationUsesNativeVerifiedTokenNotRawNonce()=runBlocking {
        val paths=mutableListOf<String>()
        val client=EmailAuth(call={path,body,token,method->
            paths+=path;assertEquals("",token);assertEquals("POST",method)
            when(path) {
                "/auth/v1/verification/verify"->{assertEquals("message",body.getString("verification_id"));JSONObject().put("verification_token","verified")}
                "/auth/v1/signup"->{assertEquals("verified",body.getString("verification_token"));assertEquals(address,body.getString("email"));assertFalse(body.has("verification_code"));JSONObject().put("sub","new-uid")}
                else->error("Unexpected request")
            }
        },now={0})
        val result=client.register(EmailChallenge("message",address,EmailPurpose.REGISTER,10000,0),address,"123456","Aa123456!")
        assertEquals("new-uid",result.getString("sub"));assertEquals(2,paths.size)
    }
    @Test fun passwordRecoveryDoesNotExposeUnknownEmailInResponse()=runBlocking {
        val client=EmailAuth(call={_,_,_,_->throw TodoCloudError(400,"user_not_found")},now={0})
        val challenge=client.send(address,EmailPurpose.RESET)
        assertEquals("",challenge.id);assertEquals(address,challenge.email)
        try {client.reset(challenge,address,"123456","Aa123456!");fail("Unknown mailbox must not reset")} catch(_:IllegalArgumentException) {}
    }
    @Test fun contactBindingRequiresCurrentAccountAndVerifiesUidAfterPatch()=runBlocking {
        val session=TodoSession("original","朋友","access","refresh",100000)
        var patch=false
        val client=EmailAuth(call={path,body,token,method->when(path) {
            "/auth/v1/verification/verify"->JSONObject().put("verification_token","verified")
            "/auth/v1/user/contact"->{assertEquals("PATCH",method);assertEquals("access",token);assertEquals("sudo",body.getString("sudo_token"));patch=true;JSONObject()}
            "/auth/v1/user/me"->{assertEquals("GET",method);JSONObject().put("sub","wrong-user").put("email",address)}
            else->error("Unexpected request")
        }},now={0})
        try {client.bind(session,"sudo",EmailChallenge("id",address,EmailPurpose.BIND,1000,0),address,"123456");fail("Must reject identity change")} catch(_:IllegalArgumentException) {}
        assertTrue(patch)
    }
    @Test fun resetRevokesRefreshAndBusinessSessionsWithoutSavingRecoverySession()=runBlocking {
        val paths=mutableListOf<String>()
        val client=EmailAuth(call={path,body,token,method->paths+=path
            when(path) {
                "/auth/v1/verification/verify"->JSONObject().put("verification_token","verified")
                "/auth/v1/reset"->{assertEquals("verified",body.getString("verification_token"));assertEquals(address,body.getString("email"));JSONObject()}
                "/auth/v1/signin"->JSONObject().put("access_token","recovery")
                "/auth/v1/user/revoke/all"->{assertEquals("recovery",token);assertEquals("DELETE",method);JSONObject()}
                "/v1/rdb/rest/rpc/wy_revoke_old_sessions"->{assertEquals("recovery",token);JSONObject().put("revoked",true).put("wait_seconds",0)}
                else->error("Unexpected request")
            }
        },now={0})
        assertTrue(client.reset(EmailChallenge("id",address,EmailPurpose.RESET,1000,0),address,"123456","Aa123456!"))
        assertEquals(5,paths.size)
    }
    @Test fun resetReportsIncompleteRevocationAfterPasswordAlreadyChanged()=runBlocking {
        val client=EmailAuth(call={path,_,_,_->when(path) {
            "/auth/v1/verification/verify"->JSONObject().put("verification_token","verified")
            "/auth/v1/reset"->JSONObject()
            else->throw TodoCloudError(503,"unavailable")
        }},now={0})
        assertFalse(client.reset(EmailChallenge("id",address,EmailPurpose.RESET,1000,0),address,"123456","Aa123456!"))
    }
    @Test fun challengeLifetimeCappedAndAddressCanonicalized()=runBlocking {
        val client=EmailAuth(call={_,body,_,_->assertEquals(address,body.getString("email"));JSONObject().put("verification_id","id").put("expires_in",999999)},now={1000})
        val value=client.send(" Friend@Example.Test ",EmailPurpose.REGISTER)
        assertEquals(601000,value.expiresAt);assertEquals(61000,value.resendAt)
    }
}
