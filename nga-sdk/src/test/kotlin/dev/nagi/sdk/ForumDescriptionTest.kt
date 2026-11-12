package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class ForumDescriptionTest {
    @Test fun directoryPreservesInfoAndLeavesMissingDescriptionsEmpty() {
        val raw = """{"data":{"0":{"name":"游戏","groups":[{"name":"魔兽世界","forums":[
            {"fid":7,"name":"议事厅","info":"魔兽 &amp; 日常讨论"},
            {"fid":8,"name":"没有说明"},
            {"fid":9,"name":"空说明","info":null},
            {"fid":0,"stid":99,"name":"合集","info":"主题合集说明"}
        ]}]}}}"""
        val forums = NgaParser.forums(raw)
        assertEquals("魔兽 & 日常讨论", forums[0].description)
        assertEquals("", forums[1].description)
        assertEquals("", forums[2].description)
        assertEquals("主题合集说明", forums[3].description)
        assertEquals("99", forums[3].collectionId)
        assertEquals("游戏", forums[0].category)
        assertEquals("魔兽世界", forums[0].group)
    }
}
