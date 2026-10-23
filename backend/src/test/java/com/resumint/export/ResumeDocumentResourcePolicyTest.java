package com.resumint.export;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ResumeDocumentResourcePolicyTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void acceptsAValidPngPortrait(){
        ObjectNode document=document();
        document.withObject("/profile").withObject("/portrait").put("src","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        assertThatNoException().isThrownBy(()->ResumeDocumentResourcePolicy.validate(document));
    }

    @Test void rejectsSvgAndForgedImageHeaders(){
        ObjectNode document=document();
        document.withObject("/profile").withObject("/portrait").put("src","data:image/svg+xml;base64,PHN2Zy8+");
        assertThatThrownBy(()->ResumeDocumentResourcePolicy.validate(document)).hasMessageContaining("type");
        document.withObject("/profile").withObject("/portrait").put("src","data:image/png;base64,"+Base64.getEncoder().encodeToString("not-a-png".getBytes()));
        assertThatThrownBy(()->ResumeDocumentResourcePolicy.validate(document)).hasMessageContaining("header");
    }

    @Test void rejectsOversizedDimensionsAndUnsafeLinks(){
        byte[] header=new byte[24];byte[] signature={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};System.arraycopy(signature,0,header,0,signature.length);
        System.arraycopy("IHDR".getBytes(),0,header,12,4);
        header[16]=0x00;header[17]=0x00;header[18]=0x13;header[19]=(byte)0x89;header[23]=0x01;
        ObjectNode document=document();
        document.withObject("/profile").withObject("/portrait").put("src","data:image/png;base64,"+Base64.getEncoder().encodeToString(header));
        assertThatThrownBy(()->ResumeDocumentResourcePolicy.validate(document)).hasMessageContaining("dimensions");
        document.withObject("/profile").remove("portrait");document.withObject("/profile").putObject("website").put("href","javascript:alert(1)");
        assertThatThrownBy(()->ResumeDocumentResourcePolicy.validate(document)).hasMessageContaining("HTTPS");
    }

    @Test void rejectsUnboundedArrays(){
        ObjectNode document=document();ArrayNode values=document.putArray("oversized");for(int index=0;index<501;index++)values.add(index);
        assertThatThrownBy(()->ResumeDocumentResourcePolicy.validate(document)).hasMessageContaining("array");
    }

    private ObjectNode document(){ObjectNode root=mapper.createObjectNode();root.putObject("profile");return root;}
}
