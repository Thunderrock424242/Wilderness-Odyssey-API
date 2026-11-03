package com.thunder.aether.server.playground;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaygroundStoreTest {
    @TempDir Path directory;
    @Test void durableReplayConflictAndImmutableSnapshot() throws Exception {
        JsonObject r=PlaygroundContractTest.training(); String id=r.get("requestId").getAsString(); JsonObject accepted;
        try (var store=new PlaygroundStore(directory,"test-server")) {
            accepted=store.submit(PlaygroundContract.training(r,"test-server"),8);
            assertEquals("queued",accepted.get("state").getAsString());
            assertEquals(accepted,store.submit(PlaygroundContract.training(r,"test-server"),8));
            r.addProperty("reason","A different reviewed reason");
            assertThrows(PlaygroundStore.Conflict.class,()->store.submit(PlaygroundContract.training(r,"test-server"),8));
            assertTrue(store.dataset(id).contains("Welcome"));
        }
        try (var store=new PlaygroundStore(directory,"test-server")) {
            assertEquals(accepted,store.find(id)); assertTrue(store.dataset(id).contains("Welcome"));
        }
    }
    @Test void durableCancelBeforeAcceptAndReceiptReconciliation() throws Exception {
        JsonObject r=PlaygroundContractTest.training(); String id=r.get("requestId").getAsString();
        try (var store=new PlaygroundStore(directory,"test-server")) {
            assertTrue(store.cancel(id).get("cancelledBeforeAcceptance").getAsBoolean()); assertNull(store.find(id));
        }
        try (var store=new PlaygroundStore(directory,"test-server")) {
            JsonObject job=store.submit(PlaygroundContract.training(r,"test-server"),8);
            assertEquals("cancelled",job.get("state").getAsString()); assertNull(store.claim());
            assertEquals(job,store.find(id)); assertEquals(job,store.cancel(id));
        }
    }
    @Test void concurrencyClaimsOnceAndRestartNeverRerunsStartedWork() throws Exception {
        String id;
        try (var store=new PlaygroundStore(directory,"test-server")) {
            var r=PlaygroundContractTest.training(); id=r.get("requestId").getAsString();
            try (var threads=Executors.newFixedThreadPool(8)) {
                List<Future<JsonObject>> results=new ArrayList<>();
                for(int i=0;i<8;i++) results.add(threads.submit(()->store.submit(PlaygroundContract.training(r,"test-server"),8)));
                Set<String> jobs=new HashSet<>(); for(var f:results) jobs.add(f.get().get("jobId").getAsString()); assertEquals(1,jobs.size());
            }
            assertNotNull(store.claim()); assertNull(store.claim());
            assertEquals("cancel-requested",store.cancel(id).get("state").getAsString());
        }
        try (var store=new PlaygroundStore(directory,"test-server")) {
            assertEquals("cancelled",store.find(id).get("state").getAsString()); assertNull(store.claim());
        }
    }
    @Test void expiredNewRequestRejectedButAcceptedReplaySurvivesExpiryAndErasure() throws Exception {
        try (var store=new PlaygroundStore(directory,"test-server")) {
            var expired=PlaygroundContractTest.training(); expired.addProperty("expiresAt",Instant.now().minusSeconds(1).toString());
            assertThrows(IllegalArgumentException.class,()->store.submit(PlaygroundContract.training(expired,"test-server"),8));
            var r=PlaygroundContractTest.training(); var input=PlaygroundContract.training(r,"test-server");
            var accepted=store.submit(input,8); String id=accepted.get("requestId").getAsString();
            store.cancel(id); store.erasePrivateBefore(Instant.now().plusSeconds(1));
            assertNull(store.dataset(id)); assertEquals("cancelled",store.submit(input,8).get("state").getAsString());
        }
    }
    @Test void runtimeBindingSurvivesRestartAndQueueQuotaRejectsBeforeAcceptance() throws Exception {
        String id;
        try(var store=new PlaygroundStore(directory,"test-server")) {
            var input=PlaygroundContract.training(PlaygroundContractTest.training(),"test-server");
            id=store.submit(input,1,"approved-model-binding-v1").get("requestId").getAsString();
            var next=PlaygroundContract.training(PlaygroundContractTest.training(),"test-server");
            assertThrows(PlaygroundStore.Full.class,()->store.submit(next,1,"other"));
            assertNull(store.find(next.envelope().get("requestId").getAsString()));
        }
        try(var store=new PlaygroundStore(directory,"test-server")) { assertEquals("approved-model-binding-v1",store.runtimeIdentity(id)); }
    }
    @Test void backupRestorePreservesJobIdentityAndRejectsRetargetedServer() throws Exception {
        String id; JsonObject original;
        try(var store=new PlaygroundStore(directory.resolve("source"),"test-server")) {
            original=store.submit(PlaygroundContract.training(PlaygroundContractTest.training(),"test-server"),8);
            id=original.get("requestId").getAsString();
        }
        Path restored=directory.resolve("restore");java.nio.file.Files.createDirectory(restored);
        java.nio.file.Files.copy(directory.resolve("source/playground.sqlite"),restored.resolve("playground.sqlite"));
        try(var store=new PlaygroundStore(restored,"test-server")) { assertEquals(original,store.find(id)); }
        assertThrows(Exception.class,()->new PlaygroundStore(restored,"other-server"));
    }
    @Test void concurrentCancellationAndAcceptanceNeverProducesClaimableJob() throws Exception {
        try(var store=new PlaygroundStore(directory,"test-server");var threads=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<20;i++) {
                var input=PlaygroundContract.training(PlaygroundContractTest.training(),"test-server");String id=input.envelope().get("requestId").getAsString();
                var gate=new CountDownLatch(1);
                var submit=threads.submit(()->{gate.await();return store.submit(input,32);});
                var cancel=threads.submit(()->{gate.await();return store.cancel(id);});gate.countDown();
                try { submit.get(); } catch(ExecutionException quota) { assertInstanceOf(PlaygroundStore.Full.class,quota.getCause()); }
                cancel.get();
                JsonObject job=store.find(id);if(job!=null)assertEquals("cancelled",job.get("state").getAsString());
                assertNull(store.claim());
            }
        }
    }
}
