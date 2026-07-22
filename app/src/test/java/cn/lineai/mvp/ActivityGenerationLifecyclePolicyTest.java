package cn.lineai.mvp;

import org.junit.Assert;
import org.junit.Test;

public final class ActivityGenerationLifecyclePolicyTest {

    @Test
    public void homeOrTaskSwitchDoesNotStopGeneration() {
        Assert.assertFalse(ActivityGenerationLifecyclePolicy.shouldStopGenerationOnStop(false));
    }

    @Test
    public void finishingActivityStopsGeneration() {
        Assert.assertTrue(ActivityGenerationLifecyclePolicy.shouldStopGenerationOnStop(true));
    }

    @Test
    public void returningToForegroundNeverStopsGeneration() {
        Assert.assertFalse(ActivityGenerationLifecyclePolicy.shouldStopGenerationOnStart());
    }
}
