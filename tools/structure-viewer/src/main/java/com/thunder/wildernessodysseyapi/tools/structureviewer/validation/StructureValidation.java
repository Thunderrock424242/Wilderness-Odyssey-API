package com.thunder.wildernessodysseyapi.tools.structureviewer.validation;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import java.util.*;

/** Format-independent diagnostics; asset-defined state validation belongs to the model resolver. */
public final class StructureValidation {
    private StructureValidation(){}
    /** Checks geometry and typed block/entity payloads without requiring a game registry. */
    public static List<String> inspect(StructureData data){
        List<String> issues=new ArrayList<>(data.diagnostics());Set<StructureData.Position> positions=new HashSet<>();
        long duplicate=0,outside=0,invalidState=0,malformedNbt=0;
        for(var block:data.blocks()){
            if(!positions.add(block.position()))duplicate++;
            var p=block.position();
            if(p.x()<0||p.y()<0||p.z()<0||p.x()>=data.size().x()||p.y()>=data.size().y()||p.z()>=data.size().z())outside++;
            var state=data.state(block);
            if(!state.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||state.equals(StructureData.BlockState.MISSING))invalidState++;
            if(block.blockEntity()!=null&&block.blockEntity().type()!=10)malformedNbt++;
        }
        if(duplicate>0)issues.add(duplicate+" duplicate block coordinates; the last visible record is shown.");
        if(outside>0)issues.add(outside+" blocks outside the declared dimensions.");
        if(invalidState>0)issues.add(invalidState+" invalid block IDs or missing palette references.");
        if(malformedNbt>0)issues.add(malformedNbt+" malformed block-entity NBT payloads retained for inspection.");
        for(var e:data.entities())if(e.type()!=10||!e.compound().containsKey("nbt")||e.compound().get("nbt").type()!=10)
            {issues.add("Entity with missing/malformed NBT payload.");break;}
        return List.copyOf(issues);
    }
}
