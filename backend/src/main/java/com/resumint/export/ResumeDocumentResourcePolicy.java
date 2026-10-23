package com.resumint.export;

import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class ResumeDocumentResourcePolicy {
    private static final int MAX_DEPTH=16;
    private static final int MAX_NODES=20_000;
    private static final int MAX_ARRAY_ITEMS=500;
    private static final int MAX_OBJECT_FIELDS=100;
    private static final int MAX_TEXT_CHARS=10_000;
    private static final int MAX_IMAGE_BYTES=2*1024*1024;
    private static final int MAX_IMAGE_EDGE=4096;
    private static final long MAX_IMAGE_PIXELS=16_777_216L;

    private ResumeDocumentResourcePolicy() { }

    public static void validate(JsonNode document) {
        int[] nodes={0};
        walk(document,"",0,nodes);
        JsonNode portrait=document.path("profile").path("portrait");
        if(!portrait.isMissingNode()){
            if(!portrait.isObject()||!portrait.has("src"))throw new IllegalArgumentException("portrait source is required");
            validatePortrait(portrait.path("src"));
        }
    }

    private static void walk(JsonNode node,String path,int depth,int[] nodes){
        if(depth>MAX_DEPTH)throw new IllegalArgumentException("document nesting exceeds the limit");
        if(++nodes[0]>MAX_NODES)throw new IllegalArgumentException("document node count exceeds the limit");
        if(node.isArray()){
            if(node.size()>MAX_ARRAY_ITEMS)throw new IllegalArgumentException("document array exceeds the item limit");
            for(int index=0;index<node.size();index++)walk(node.get(index),path+"/"+index,depth+1,nodes);
        }else if(node.isObject()){
            if(node.size()>MAX_OBJECT_FIELDS)throw new IllegalArgumentException("document object exceeds the field limit");
            Iterator<Map.Entry<String,JsonNode>> fields=node.properties().iterator();
            while(fields.hasNext()){
                Map.Entry<String,JsonNode> field=fields.next();
                String childPath=path+"/"+field.getKey();
                if(("href".equals(field.getKey())||"link".equals(field.getKey()))&&field.getValue().isTextual())validateLink(field.getValue().asString());
                walk(field.getValue(),childPath,depth+1,nodes);
            }
        }else if(node.isTextual()&&!"/profile/portrait/src".equals(path)&&node.asString().length()>MAX_TEXT_CHARS){
            throw new IllegalArgumentException("document text exceeds the field limit");
        }
    }

    private static void validateLink(String value){
        if(value.length()>2048||!value.startsWith("https://"))throw new IllegalArgumentException("document links must use HTTPS");
    }

    private static void validatePortrait(JsonNode sourceNode){
        if(!sourceNode.isTextual())throw new IllegalArgumentException("portrait source must be text");
        String source=sourceNode.asString();
        if(source.matches("^/assets/templates/[a-z0-9-]+\\.(png|jpeg|webp)$"))return;
        int separator=source.indexOf(",");
        if(separator<0)throw new IllegalArgumentException("portrait must be a supported image");
        String media=source.substring(0,separator);
        if(!media.matches("data:image/(png|jpeg|webp);base64"))throw new IllegalArgumentException("portrait type is not allowed");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(source.substring(separator+1));}
        catch(IllegalArgumentException exception){throw new IllegalArgumentException("portrait base64 is invalid");}
        if(bytes.length>MAX_IMAGE_BYTES)throw new IllegalArgumentException("portrait exceeds the byte limit");
        int[] dimensions=media.contains("png")?png(bytes):media.contains("jpeg")?jpeg(bytes):webp(bytes);
        if(dimensions[0]<1||dimensions[1]<1||dimensions[0]>MAX_IMAGE_EDGE||dimensions[1]>MAX_IMAGE_EDGE||(long)dimensions[0]*dimensions[1]>MAX_IMAGE_PIXELS)
            throw new IllegalArgumentException("portrait dimensions exceed the limit");
    }

    private static int[] png(byte[] bytes){
        byte[] signature={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};
        if(bytes.length<24||!ascii(bytes,12,"IHDR"))return invalid();
        for(int i=0;i<signature.length;i++)if(bytes[i]!=signature[i])return invalid();
        return new int[]{bigEndian(bytes,16),bigEndian(bytes,20)};
    }

    private static int[] jpeg(byte[] bytes){
        if(bytes.length<4||(bytes[0]&255)!=0xff||(bytes[1]&255)!=0xd8)return invalid();
        int offset=2;
        while(offset+8<bytes.length){
            if((bytes[offset]&255)!=0xff){offset++;continue;}
            int marker=bytes[offset+1]&255;offset+=2;
            if(marker==0xd8||marker==0xd9)continue;
            if(offset+2>bytes.length)return invalid();
            int length=((bytes[offset]&255)<<8)|(bytes[offset+1]&255);
            if(length<2||offset+length>bytes.length)return invalid();
            if((marker>=0xc0&&marker<=0xc3)||(marker>=0xc5&&marker<=0xc7)||(marker>=0xc9&&marker<=0xcb)||(marker>=0xcd&&marker<=0xcf))
                return new int[]{((bytes[offset+5]&255)<<8)|(bytes[offset+6]&255),((bytes[offset+3]&255)<<8)|(bytes[offset+4]&255)};
            offset+=length;
        }
        return invalid();
    }

    private static int[] webp(byte[] bytes){
        if(bytes.length<30||!ascii(bytes,0,"RIFF")||!ascii(bytes,8,"WEBP"))return invalid();
        if(ascii(bytes,12,"VP8X"))return new int[]{1+little24(bytes,24),1+little24(bytes,27)};
        if(ascii(bytes,12,"VP8L")&&(bytes[20]&255)==0x2f){int b0=bytes[21]&255,b1=bytes[22]&255,b2=bytes[23]&255,b3=bytes[24]&255;return new int[]{1+(b0|((b1&0x3f)<<8)),1+((b1>>6)|(b2<<2)|((b3&0x0f)<<10))};}
        if(ascii(bytes,12,"VP8 ")&&(bytes[23]&255)==0x9d&&(bytes[24]&255)==0x01&&(bytes[25]&255)==0x2a)return new int[]{((bytes[26]&255)|((bytes[27]&255)<<8))&0x3fff,((bytes[28]&255)|((bytes[29]&255)<<8))&0x3fff};
        return invalid();
    }

    private static boolean ascii(byte[] bytes,int offset,String expected){if(offset+expected.length()>bytes.length)return false;for(int i=0;i<expected.length();i++)if((bytes[offset+i]&255)!=expected.charAt(i))return false;return true;}
    private static int bigEndian(byte[] bytes,int offset){return ((bytes[offset]&255)<<24)|((bytes[offset+1]&255)<<16)|((bytes[offset+2]&255)<<8)|(bytes[offset+3]&255);}
    private static int little24(byte[] bytes,int offset){return (bytes[offset]&255)|((bytes[offset+1]&255)<<8)|((bytes[offset+2]&255)<<16);}
    private static int[] invalid(){throw new IllegalArgumentException("portrait image header is invalid");}
}
