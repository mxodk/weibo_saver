package com.mxodk.weibosaver.download;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 将 Oplus 元数据写入原 JPEG 码流，并追加已封装的 MP4。 */
final class OplusPhotoPacker {
    private static final String XMP_HEADER = "http://ns.adobe.com/xap/1.0/\0";

    static int pack(Path jpeg, Path mp4, Path target, int orientation, Runnable check) throws IOException {
        check.run();
        byte[] source = Files.readAllBytes(jpeg);
        if (source.length < 4 || u8(source, 0) != 255 || u8(source, 1) != 216
                || u8(source, source.length - 2) != 255 || u8(source, source.length - 1) != 217) {
            throw new IOException("JPEG 缺少完整主图");
        }
        if (orientation < 1 || orientation > 8) throw new IOException("EXIF 方向无效");
        long videoLength = Files.size(mp4);
        if (videoLength <= 0) throw new IOException("MP4 文件为空");
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(source, 0, 2);
        // 保留非目标 APP 段和主图数据，替换原 EXIF、XMP、MPF。
        int cursor = 2;
        while (cursor + 4 <= source.length && u8(source, cursor) == 255) {
            int marker = u8(source, cursor + 1);
            if (marker < 224 || marker > 239) break;
            int length = u8(source, cursor + 2) * 256 + u8(source, cursor + 3);
            int end = cursor + 2 + length;
            if (length < 2 || end > source.length) throw new IOException("JPEG 元数据越界");
            int body = cursor + 4;
            boolean replace = marker == 225 && (starts(source, body, end, "Exif\0\0")
                    || starts(source, body, end, XMP_HEADER)
                    || starts(source, body, end, "http://ns.adobe.com/xmp/extension/\0"))
                    || marker == 226 && starts(source, body, end, "MPF\0");
            if (!replace) header.write(source, cursor, end - cursor);
            cursor = end;
        }
        header.write(segment(225, exif(orientation)));
        header.write(segment(225, bytes(xmp(videoLength))));
        int imageLength = Math.addExact(header.size(), source.length - cursor);
        imageLength = Math.addExact(imageLength, segment(226, mpf(0)).length);
        header.write(segment(226, mpf(imageLength)));
        try (OutputStream out = Files.newOutputStream(target)) {
            header.writeTo(out);
            out.write(source, cursor, source.length - cursor);
            try (var in = Files.newInputStream(mp4)) {
                byte[] buffer = new byte[65536];
                for (int count; (count = in.read(buffer)) >= 0;) {
                    check.run();
                    out.write(buffer, 0, count);
                }
            }
        }
        return imageLength;
    }

    private static byte[] exif(int orientation) {
        byte[] comment = bytes("ASCII\0\0\0Oplus_8388608");
        ByteBuffer tiff = ByteBuffer.allocate(6 + 56 + comment.length);
        tiff.put(bytes("Exif\0\0MM")).putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        entry(tiff, 0x0112, 3, 1, orientation << 16);
        entry(tiff, 0x8769, 4, 1, 38);
        tiff.putInt(0).putShort((short) 1);
        entry(tiff, 0x9286, 7, comment.length, 56);
        tiff.putInt(0).put(comment);
        return tiff.array();
    }

    private static byte[] mpf(int size) {
        ByteBuffer b = ByteBuffer.allocate(70);
        b.put(bytes("MPF\0MM")).putShort((short) 42).putInt(8).putShort((short) 3);
        entry(b, 0xb000, 7, 4, 0x30313030);
        entry(b, 0xb001, 4, 1, 1);
        entry(b, 0xb002, 7, 16, 50);
        b.putInt(0).putInt(0x00030000).putInt(size).putInt(0).putInt(0);
        return b.array();
    }

    private static void entry(ByteBuffer b, int tag, int type, int count, int value) {
        b.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static String xmp(long size) {
        return XMP_HEADER + "<x:xmpmeta xmlns:x='adobe:ns:meta/'>"
                + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
                + "<rdf:Description rdf:about='' xmlns:GCamera='http://ns.google.com/photos/1.0/camera/' "
                + "xmlns:OpCamera='http://ns.oplus.com/photos/1.0/camera/' "
                + "xmlns:Container='http://ns.google.com/photos/1.0/container/' "
                + "xmlns:Item='http://ns.google.com/photos/1.0/container/item/' "
                + "GCamera:MotionPhoto='1' GCamera:MotionPhotoVersion='1' GCamera:MotionPhotoPresentationTimestampUs='0' "
                + "OpCamera:MotionPhotoPrimaryPresentationTimestampUs='0' OpCamera:MotionPhotoOwner='oplus' "
                + "OpCamera:OLivePhotoVersion='2' OpCamera:MotionPhotoFeatureFlag='1' OpCamera:VideoLength='" + size + "'>"
                + "<Container:Directory><rdf:Seq><rdf:li rdf:parseType='Resource'>"
                + "<Container:Item Item:Mime='image/jpeg' Item:Semantic='Primary' Item:Length='0' Item:Padding='0'/>"
                + "</rdf:li><rdf:li rdf:parseType='Resource'>"
                + "<Container:Item Item:Mime='video/mp4' Item:Semantic='MotionPhoto' Item:Length='" + size + "' Item:Padding='0'/>"
                + "</rdf:li></rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>";
    }

    private static byte[] segment(int marker, byte[] body) {
        return ByteBuffer.allocate(body.length + 4).put((byte) 255).put((byte) marker)
                .putShort((short) (body.length + 2)).put(body).array();
    }

    private static boolean starts(byte[] data, int start, int end, String prefix) {
        byte[] pattern = bytes(prefix);
        if (end - start < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (data[start + i] != pattern[i]) return false;
        return true;
    }

    private static int u8(byte[] data, int i) { return data[i] & 255; }
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

}
