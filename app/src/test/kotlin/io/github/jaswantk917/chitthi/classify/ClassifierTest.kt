package io.github.jaswantk917.chitthi.classify

import io.github.jaswantk917.chitthi.data.Brands
import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClassifierTest {
    private fun sort(from: String, body: String, contact: Boolean = false) =
        Classifier.classify(SenderId.parse(from), body, contact)

    @Test fun `card transaction is important with a debit tag`() {
        val v = sort("AX-ICICIT-S", "ICICI Bank Credit Card XX4321 has been used for a transaction of INR 1,240.00 on 07-Nov-26 at SWIGGY.")
        assertEquals(Category.Important, v.category)
        assertTrue(v.tags.any { it is Tag.Debit })
    }

    @Test fun `promo route is promotions`() =
        assertEquals(Category.Promotions, sort("JD-JIOINF-P", "Unlock the full potential of your JioFiber with exclusive offers! Click jio.com/x").category)

    @Test fun `old header promo is promotions`() =
        assertEquals(Category.Promotions, sort("VM-HTHWAY", "Enjoy higher speed at no extra cost! Upgrade now, offer valid till 30 Nov. T&C apply").category)

    @Test fun `otp from app header is important`() {
        val v = sort("BP-BIGBSK", "Bigbasket login code: 814946. Valid for 10 minutes.")
        assertEquals(Category.Important, v.category)
        assertEquals("814946", v.otp)
    }

    @Test fun `a code that expires is not a bill`() {
        val v = sort("JM-KUBERB", "482913 is your OTP to log in to Kuber Bank. It expires in 5 minutes. Never share it with anyone.")
        assertEquals(listOf(Tag.Otp("482913")), v.tags)
        assertEquals(listOf(Tag.Otp("814946")), sort("BP-BIGBSK", "Bigbasket login code: 814946. Validity 10 minutes.").tags)
    }

    @Test fun `awareness message without signals is an unsure promo`() {
        val v = sort("VK-KFINTK", "Vigilance Awareness Week is observed from 27 Oct to 2 Nov. Let us all pledge for integrity.")
        assertEquals(Category.Promotions, v.category)
        assertTrue(v.unsure)
    }

    @Test fun `saved contact is personal even with bank words`() =
        assertEquals(Category.Personal, sort("+919876500001", "Dear Customer, you have 2 missed calls", contact = true).category)

    @Test fun `unknown number chatting is unknown`() =
        assertEquals(Category.Unknown, sort("+919876543210", "Hi, is this the flat owner?").category)

    @Test fun `short code pickup reminder is important`() =
        assertEquals(Category.Important, sort("57575022", "Pickup reminder: Your return pickup is scheduled for tomorrow. Keep the item packed.").category)

    @Test fun `hindi data pack expiry is important`() =
        assertEquals(Category.Important, sort("JM-JIOINF-S", "डेटा पैक 06-Nov-26 01:04 Hrs को समाप्त हो जाएगा। रिचार्ज करें").category)
}

class BrandsTest {
    private val brands = Brands(File("src/main/assets/brands.json").readText())

    @Test fun exact() = assertEquals("ICICI Bank", brands.find("ICICIB")?.name)

    @Test fun prefix() = assertEquals("ICICI Bank", brands.find("ICICIO")?.name)

    @Test fun `exact beats a shorter brand's prefix`() = assertEquals("JioHotstar", brands.find("JIOHTS")?.name)

    @Test fun `card header goes to the card brand`() = assertEquals("SBI Card", brands.find("SBICRD")?.name)

    @Test fun `unknown stays unknown`() = assertEquals(null, brands.find("JBRRIA"))
}
