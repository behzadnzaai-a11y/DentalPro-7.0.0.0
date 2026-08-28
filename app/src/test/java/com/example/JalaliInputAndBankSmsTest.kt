package com.example

import com.example.ui.screens.formatJalaliDayFirstInput
import com.example.ui.screens.formatJalaliYearFirstInput
import com.example.ui.screens.parseJalaliDayFirstInput
import com.example.util.BankSmsEngine
import com.example.util.PasswordSmsFilter
import com.example.util.SmsNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های منطقیِ تغییرات این نسخه:
 * - قالب‌بندی ورودی عددی تاریخ شمسی با جداساز ثابت «/»
 * - سال خودکار 14xx در فیلد تاریخ نوبت
 * - موتور امتیازدهی ۵ بخشی پیامک بانکی + فیلتر رمز/کارمزد + نرمال‌سازی
 */
class JalaliInputAndBankSmsTest {

    // ---------- تاریخ تولد (سال/ماه/سال اول) ----------

    @Test
    fun testBirthYearOnlyStaysFourDigits() {
        assertEquals("1375", formatJalaliYearFirstInput("1375"))
    }

    @Test
    fun testBirthDateAutoSlashAfterYearAndMonth() {
        assertEquals("1375/04", formatJalaliYearFirstInput("137504"))
        assertEquals("1375/04/10", formatJalaliYearFirstInput("13750410"))
    }

    @Test
    fun testBirthDateStripsNonDigitsAndCapsLength() {
        assertEquals("1375/04/10", formatJalaliYearFirstInput("a1b3c7!5/0*4-1@0#9"))
        // بیش از ۸ رقم نمی‌پذیرد
        assertEquals("1405/06/99", formatJalaliYearFirstInput("1405069912"))
    }

    // ---------- تاریخ نوبت (روز/ماه/سال با سال خودکار 14xx) ----------

    @Test
    fun testAppointmentDateAutoSlashDayMonth() {
        assertEquals("04", formatJalaliDayFirstInput("04"))
        assertEquals("04/06", formatJalaliDayFirstInput("0406"))
    }

    @Test
    fun testAppointmentYearAutoPrefixes14() {
        // کاربر فقط دو رقم آخر سال را می‌زند؛ «14» خودکار وارد می‌شود
        assertEquals("04/06/145", formatJalaliDayFirstInput("04065"))
        assertEquals("04/06/1405", formatJalaliDayFirstInput("040605"))
    }

    @Test
    fun testAppointmentFullYearRoundTrips() {
        // مقدار کاملِ آمده از تقویم شمسی هنگام ویرایش دست‌نخورده می‌ماند
        assertEquals("04/06/1405", formatJalaliDayFirstInput("04061405"))
        assertEquals("04/06/140", formatJalaliDayFirstInput("0406140"))
    }

    @Test
    fun testParseValidAppointmentDate() {
        val parsed = parseJalaliDayFirstInput("04/06/1405")
        assertNotNull(parsed)
        assertEquals(4, parsed!!.first)
        assertEquals(6, parsed.second)
        assertEquals(1405, parsed.third)
    }

    @Test
    fun testParseTwoDigitYearInterpretedAs14xx() {
        val parsed = parseJalaliDayFirstInput("04/06/05")
        assertNotNull(parsed)
        assertEquals(1405, parsed!!.third)
    }

    @Test
    fun testParseRejectsInvalidDates() {
        assertNull(parseJalaliDayFirstInput("32/13/1405")) // روز/ماه نامعتبر
        assertNull(parseJalaliDayFirstInput("04/06"))       // ناقص
        assertNull(parseJalaliDayFirstInput("04/06/1200"))  // سال خارج از بازه
    }

    // ---------- نرمال‌سازی و فیلتر پیامک ----------

    @Test
    fun testSmsNormalizerToEnglishDigits() {
        assertEquals("123", SmsNormalizer.toEnglishDigits("۱۲۳"))
        assertEquals("456", SmsNormalizer.toEnglishDigits("٤٥٦"))
    }

    @Test
    fun testSmsNormalizerArabicPersianLetters() {
        assertEquals("کیت ی", SmsNormalizer.normalizeArabicPersianLetters("كیت ي"))
    }

    @Test
    fun testPasswordSmsFilterBlocksOtp() {
        val otp = "بانک ملت\nرمز پویا: 834512\nاین رمز را در اختیار دیگران قرار ندهید"
        assertTrue(PasswordSmsFilter.isPasswordOrFeeSms(otp))
    }

    @Test
    fun testPasswordSmsFilterAllowsDeposit() {
        val deposit = "بانک ملت واریز مبلغ 5,000,000 ریال به حساب 1234*. عالی"
        assertFalse(PasswordSmsFilter.isPasswordOrFeeSms(deposit))
    }

    // ---------- موتور امتیازدهی ۵ بخشی ----------

    @Test
    fun testEvaluateSmsDepositTransaction() {
        val sms = "بانک ملت\nواریز مبلغ 5000000 ریال به حساب 1234567890\nتاریخ 1404/06/12 ساعت 14:35"
        val candidate = BankSmsEngine.evaluateSms(sms, "9820000")
        assertNotNull(candidate)
        assertTrue(candidate!!.score >= 3)
        assertEquals("DEPOSIT", candidate.transactionType)
        assertEquals("1404/06/12", candidate.dateStr)
        assertEquals("14:35", candidate.timeStr)
    }

    @Test
    fun testEvaluateSmsRejectsPasswordSms() {
        val otp = "رمز یکبار مصرف شما: 45921 بانک ملی"
        assertNull(BankSmsEngine.evaluateSms(otp, "9830000"))
    }

    @Test
    fun testEvaluateSmsRejectsNoAmount() {
        val noAmount = "بانک ملت موجودی شما به روز شد. با احترام بانک ملت"
        assertNull(BankSmsEngine.evaluateSms(noAmount, "9820000"))
    }

    @Test
    fun testBankSenderDetection() {
        assertTrue(BankSmsEngine.isBankSender("9820000"))
        assertEquals("بانک ملت", BankSmsEngine.detectBankNameBySender("9820000"))
        assertFalse(BankSmsEngine.isBankSender("+989121234567"))
    }
}
