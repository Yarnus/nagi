package dev.nagi.sdk

import org.junit.Test
import org.junit.Assert.*

class PreparationIdentityTest {
    @Test fun nonemptyPreparationRequiresMatchingContext() {
        val target = PublishTarget(PublishAction.QUOTE, topicId = "123", postId = "12")
        val valid = "<root><action>quote</action><tid>123</tid><pid>12</pid><auth>test</auth><__CU><uid>7</uid></__CU><content>Context</content></root>"
        assertEquals("Context", NgaXmlParser.publishPreparation(valid, target).content)
        listOf(valid.replace("quote", "reply"), valid.replace("123", "999"),
            valid.replace("<pid>12</pid>", "<pid>13</pid>"),
            valid.replace("<auth>test</auth>", ""), valid.replace("<uid>7</uid>", "<uid>0</uid>"))
            .forEach { assertTrue(runCatching { NgaXmlParser.publishPreparation(it, target) }.exceptionOrNull() is NgaException.Protocol) }
    }
}
