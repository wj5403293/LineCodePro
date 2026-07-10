package cn.lineai.util;

import org.junit.Assert;
import org.junit.Test;

public final class StringUtilsTest {
    @Test
    public void decodesBasicUnicodeEscapes() {
        Assert.assertEquals("模型通信失败", StringUtils.decodeUnicodeEscapes("\\u6a21\\u578b\\u901a\\u4fe1\\u5931\\u8d25"));
    }

    @Test
    public void leavesPlainTextUnchanged() {
        Assert.assertEquals("HTTP 404: not found", StringUtils.decodeUnicodeEscapes("HTTP 404: not found"));
    }

    @Test
    public void keepsIncompleteEscapeSequences() {
        Assert.assertEquals("\\u6", StringUtils.decodeUnicodeEscapes("\\u6"));
        Assert.assertEquals("\\uZZZZ", StringUtils.decodeUnicodeEscapes("\\uZZZZ"));
    }

    @Test
    public void doesNotDecodeWindowsStylePaths() {
        Assert.assertEquals("\\Users\\lang中", StringUtils.decodeUnicodeEscapes("\\Users\\lang\\u4e2d"));
    }

    @Test
    public void handlesNullAndShortInput() {
        Assert.assertNull(StringUtils.decodeUnicodeEscapes(null));
        Assert.assertEquals("ab", StringUtils.decodeUnicodeEscapes("ab"));
    }

    @Test
    public void decodesMixedContent() {
        Assert.assertEquals("错误: 模型不存在 (code 404)",
                StringUtils.decodeUnicodeEscapes("\\u9519\\u8bef: \\u6a21\\u578b\\u4e0d\\u5b58\\u5728 (code 404)"));
    }
}
