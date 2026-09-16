package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.NbtValue;
import java.io.IOException;
import java.util.*;

/** Small bounded SNBT adapter for Blueprint-v1 block/entity payloads, preserving numeric tag types. */
final class SnbtReader {
    private final String text; private int cursor;
    private SnbtReader(String text){this.text=text;}
    static NbtValue read(String text) throws IOException {
        if(text.length()>1_048_576)throw new IOException("SNBT payload exceeds 1 MiB");
        SnbtReader reader=new SnbtReader(text);NbtValue value=reader.value(0);reader.space();
        if(reader.cursor!=text.length())throw reader.error("Trailing SNBT input");return value;
    }
    private IOException error(String message){return new IOException(message+" at character "+cursor);}
    private void space(){while(cursor<text.length()&&Character.isWhitespace(text.charAt(cursor)))cursor++;}
    private char peek() throws IOException {space();if(cursor>=text.length())throw error("Unexpected end of SNBT");return text.charAt(cursor);}
    private boolean consume(char c){space();if(cursor<text.length()&&text.charAt(cursor)==c){cursor++;return true;}return false;}
    private void expect(char c)throws IOException{if(!consume(c))throw error("Expected '"+c+"'");}
    private String quoted() throws IOException {
        char quote=text.charAt(cursor++);StringBuilder result=new StringBuilder();
        while(cursor<text.length()){
            char c=text.charAt(cursor++);if(c==quote)return result.toString();
            if(c=='\\'){if(cursor>=text.length())throw error("Incomplete escape");c=text.charAt(cursor++);}
            result.append(c);
        }
        throw error("Unclosed quoted string");
    }
    private String token(boolean key)throws IOException{
        space();char c=peek();if(c=='"'||c=='\'')return quoted();
        int start=cursor;
        while(cursor<text.length()){
            c=text.charAt(cursor);if(Character.isWhitespace(c)||c==','||c==']'||c=='}'||(key&&c==':'))break;cursor++;
        }
        if(start==cursor)throw error("Expected value");return text.substring(start,cursor);
    }
    private NbtValue value(int depth)throws IOException{
        if(depth>64)throw error("SNBT nesting limit exceeded");
        char c=peek();
        if(c=='{'){
            cursor++;Map<String,NbtValue> map=new LinkedHashMap<>();
            if(!consume('}'))do{String key=token(true);expect(':');if(map.putIfAbsent(key,value(depth+1))!=null)throw error("Duplicate key "+key);
                if(consume('}'))return new NbtValue(10,map);expect(',');}while(true);
            return new NbtValue(10,map);
        }
        if(c=='['){
            cursor++;space();int arrayType=9,elementType=-1;
            if(cursor+1<text.length()&&text.charAt(cursor+1)==';'){
                char kind=Character.toUpperCase(text.charAt(cursor));arrayType=kind=='B'?7:kind=='I'?11:kind=='L'?12:0;
                if(arrayType==0)throw error("Invalid typed array");elementType=kind=='B'?1:kind=='I'?3:4;cursor+=2;
            }
            List<NbtValue> values=new ArrayList<>();
            if(!consume(']'))do{
                NbtValue item=value(depth+1);
                if(elementType<0)elementType=item.type();
                if(item.type()!=elementType)throw error("Mixed NBT list/array types");
                values.add(item);if(values.size()>100000)throw error("SNBT collection too large");
                if(consume(']'))return new NbtValue(arrayType,values);expect(',');
            }while(true);
            return new NbtValue(arrayType,values);
        }
        if(c=='"'||c=='\'')return new NbtValue(8,quoted());
        String token=token(false);
        try{
            char suffix=Character.toLowerCase(token.charAt(token.length()-1));String body=token.substring(0,token.length()-1);
            if(token.matches("[-+]?[0-9]+[bBsSlL]"))return switch(suffix){
                case 'b'->new NbtValue(1,Byte.parseByte(body));case 's'->new NbtValue(2,Short.parseShort(body));default->new NbtValue(4,Long.parseLong(body));};
            if(token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?[fFdD]"))
                return suffix=='f'?new NbtValue(5,Float.parseFloat(body)):new NbtValue(6,Double.parseDouble(body));
            if(token.equalsIgnoreCase("true")||token.equalsIgnoreCase("false"))return new NbtValue(1,(byte)(Boolean.parseBoolean(token)?1:0));
            if(token.matches("[-+]?[0-9]+"))return new NbtValue(3,Integer.parseInt(token));
            if(token.matches("[-+]?(?:[0-9]+\\.[0-9]*|\\.[0-9]+|[0-9]+[eE][-+]?[0-9]+)(?:[eE][-+]?[0-9]+)?"))
                return new NbtValue(6,Double.parseDouble(token));
        }catch(NumberFormatException ignored){/* Minecraft treats unmatched/overflowed numeric tokens as strings. */}
        return new NbtValue(8,token);
    }
}
