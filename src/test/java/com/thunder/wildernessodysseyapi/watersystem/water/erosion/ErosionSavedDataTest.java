package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErosionSavedDataTest {
    @Test void unknownAndPlayerMarkedTerrainRemainProtectedAfterReload() {
        ErosionSavedData ledger = new ErosionSavedData();
        assertFalse(ledger.eligible(7));
        ledger.enroll(7);
        assertTrue(ledger.eligible(7));
        ledger.protect(7);
        ledger.enroll(7);
        ErosionSavedData restored = ErosionSavedData.load(ledger.save(new CompoundTag(), null), null);
        assertFalse(restored.eligible(7));
        assertFalse(restored.eligible(8));
    }

    @Test void transportAndDepositConserveCreditedMaterial() {
        ErosionSavedData ledger = new ErosionSavedData();
        var sand = MaterialErosionRegistry.Material.SAND;
        ledger.enroll(1);
        ledger.enroll(2);
        assertFalse(ledger.transfer(1, 2, sand));
        ledger.credit(1, sand);
        assertFalse(ledger.transfer(1, 3, sand));
        assertEquals(1, ledger.units(1));
        assertTrue(ledger.transfer(1, 2, sand));
        assertEquals(1, ledger.units(1) + ledger.units(2));
        ErosionSavedData restored = ErosionSavedData.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(1, restored.units(2));
        restored.spend(2, sand);
        assertEquals(0, restored.units(2));
        assertThrows(IllegalStateException.class, () -> restored.spend(2, sand));
    }

    @Test void saturationRejectsNewCreditWithoutDiscardingExistingMass() {
        ErosionSavedData ledger = new ErosionSavedData();
        ledger.enroll(1);
        for (int i = 0; i < 64; i++) ledger.credit(1, MaterialErosionRegistry.Material.GRAVEL);
        assertFalse(ledger.canCredit(1, MaterialErosionRegistry.Material.GRAVEL));
        assertThrows(IllegalStateException.class, () -> ledger.credit(1, MaterialErosionRegistry.Material.GRAVEL));
        assertEquals(64, ledger.units(1));
        ledger.protect(1);
        assertEquals(64, ErosionSavedData.load(ledger.save(new CompoundTag(), null), null).units(1));
    }
}
