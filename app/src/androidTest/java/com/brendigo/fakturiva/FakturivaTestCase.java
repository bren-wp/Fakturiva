package com.brendigo.fakturiva;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import junit.framework.TestCase;

/**
 * Real AndroidX-runner compatible JUnit3-style base.
 * Android 16 no longer includes android.test.AndroidTestCase in app classpath.
 * Use an instrumented application context to test SQLite, backup and business logic.
 */
abstract class FakturivaTestCase extends TestCase {
    protected final Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
