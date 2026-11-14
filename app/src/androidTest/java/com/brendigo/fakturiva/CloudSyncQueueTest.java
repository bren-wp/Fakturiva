package com.brendigo.fakturiva;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/**
 * Instrumented concurrency regression using CloudMirror's real executor.
 * No network calls, account credentials, SQLite writes or real user data.
 */
public final class CloudSyncQueueTest {
    @Test public void manualTransfersExecuteSerially() throws Exception {
        CountDownLatch firstStarted=new CountDownLatch(1);
        CountDownLatch releaseFirst=new CountDownLatch(1);
        CountDownLatch secondStarted=new CountDownLatch(1);
        CountDownLatch finished=new CountDownLatch(2);
        AtomicBoolean firstActive=new AtomicBoolean(false);
        AtomicBoolean overlap=new AtomicBoolean(false);
        CloudMirror.executeManualTransfer(()->{
            firstActive.set(true);
            firstStarted.countDown();
            try{
                releaseFirst.await(10,TimeUnit.SECONDS);
            }catch(InterruptedException error){
                Thread.currentThread().interrupt();
            }finally{
                firstActive.set(false);
                finished.countDown();
            }
        });
        try{
            assertTrue("First mirror task did not start",
                firstStarted.await(5,TimeUnit.SECONDS));
            CloudMirror.executeManualTransfer(()->{
                if(firstActive.get())overlap.set(true);
                secondStarted.countDown();
                finished.countDown();
            });
            assertFalse("Second transfer ran concurrently with first",
                secondStarted.await(150,TimeUnit.MILLISECONDS));
        }finally{
            releaseFirst.countDown();
        }
        assertTrue("Queued transfer never completed",
            finished.await(10,TimeUnit.SECONDS));
        assertFalse("Manual transfer executor permitted overlap",overlap.get());
    }
}
