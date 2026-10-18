package com.withyou.anniversary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Opt-in host-prepared, signed version-7 fixtures. Never included in the production APK. */
@RunWith(AndroidJUnit4::class)
class UpdateInstallEvidenceTest {
    @Test fun acceptsSameSignerAndRejectsWrongSignerVersionPackageAndCorruption() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installEvidence")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val good=File(context.filesDir,"validation/update-good.apk")
        val wrong=File(context.filesDir,"validation/update-wrong-signer.apk")
        fun metadata(file:File,code:Long=7)=AppUpdate(code,"1.4.0","验证", "https://github.com/2269642185/withyou/releases/download/test/withyou.apk",file.length(),MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {"%02x".format(it)},26)
        fun rejected(file:File,update:AppUpdate,reason:String) {
            try {UpdateInstaller.verify(context,file,update);fail(reason)} catch(e:IllegalArgumentException) { assertTrue("unexpected rejection: ${e.message}",e.message.orEmpty().contains(reason)) }
        }
        UpdateInstaller.verify(context,good,metadata(good))
        rejected(wrong,metadata(wrong),"签名不一致")
        rejected(good,metadata(good,8),"版本不匹配")
        val testApk=File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir)
        rejected(testApk,metadata(testApk),"版本不匹配")
        rejected(good,metadata(good).copy(sha256="0".repeat(64)),"校验失败")
        val own=File(context.applicationInfo.sourceDir)
        rejected(own,metadata(own,BuildConfig.VERSION_CODE.toLong()),"版本不匹配")
    }
}
