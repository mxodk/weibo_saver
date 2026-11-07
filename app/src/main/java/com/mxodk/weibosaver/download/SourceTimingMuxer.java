package com.mxodk.weibosaver.download;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 复制已验证的 MOV／MP4 样本表，保留解码时序并重算块偏移。 */
final class SourceTimingMuxer {
    private static final byte[] FTYP = ByteBuffer.allocate(24).putInt(24)
            .put(ascii("ftypisom")).putInt(512).put(ascii("isommp42")).array();

    static final class UnsupportedLayout extends IOException {
        UnsupportedLayout(String reason) { super(reason); }
    }

    static boolean canEmbed(Path source, Runnable check) throws IOException {
        check.run();
        try {
            readLayout(ByteBuffer.wrap(Files.readAllBytes(source)), true, check);
            return true;
        } catch (UnsupportedLayout error) {
            return false;
        }
    }

    private static MovieLayout readLayout(ByteBuffer buffer, boolean keepMetadata, Runnable check) throws IOException {
        Box root = new Box(0, 0, buffer.capacity(), "root");
        List<Box> top = boxes(buffer, root);
        require(top.stream().noneMatch(box -> box.type.equals("moof")), "不支持分片容器");
        if (keepMetadata) {
            require(!top.isEmpty() && top.get(0).type.equals("ftyp")
                    && top.stream().allMatch(box -> List.of("ftyp", "moov", "mdat", "free", "wide", "skip").contains(box.type)),
                    "源容器顶层布局未覆盖");
            one(buffer, root, "ftyp");
        }
        Box moov = one(buffer, root, "moov");
        Box mdat = one(buffer, root, "mdat");
        Box mvhd = one(buffer, moov, "mvhd");
        if (keepMetadata) require(boxes(buffer, moov).stream().noneMatch(box -> box.type.equals("mvex")), "不支持分片容器");
        List<Box> tracks = new ArrayList<>();
        List<Chunk> chunks = new ArrayList<>();
        int video = 0;
        int audio = 0;
        for (Box track : boxes(buffer, moov)) {
            if (!track.type.equals("trak")) continue;
            check.run();
            Box mdia = one(buffer, track, "mdia");
            Box hdlr = one(buffer, mdia, "hdlr");
            require(hdlr.end - hdlr.body >= 12, "轨道类型字段不完整");
            String handler = type(buffer, hdlr.body + 8);
            if (handler.equals("vide")) video++;
            else if (handler.equals("soun")) audio++;
            else if (handler.equals("meta")) {
                if (!keepMetadata) continue;
            } else throw new UnsupportedLayout("源轨道类型未覆盖");
            tracks.add(track);
            Box minf = one(buffer, mdia, "minf");
            requireSelfContained(buffer, minf);
            Box stbl = one(buffer, minf, "stbl");
            chunks.addAll(readChunks(buffer, stbl, mdat, handler));
        }
        require(video == 1 && audio <= 1 && !chunks.isEmpty(), "需要一条视频轨及零条或一条 AAC 轨道");
        chunks.sort(Comparator.comparingInt(chunk -> chunk.source));
        int previousEnd = mdat.body;
        for (Chunk chunk : chunks) {
            require(chunk.source >= previousEnd, "源媒体块重叠");
            previousEnd = Math.addExact(chunk.source, chunk.length);
        }
        return new MovieLayout(mvhd, tracks, chunks);
    }

    static void remux(Path source, Path target, Runnable check) throws IOException {
        check.run();
        byte[] data = Files.readAllBytes(source);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        MovieLayout layout = readLayout(buffer, false, check);
        Box mvhd = layout.movieHeader;
        List<Box> tracks = layout.tracks;
        List<Chunk> chunks = layout.chunks;
        int mediaSize = 8;
        for (Chunk chunk : chunks) {
            buffer.putInt(chunk.tableOffset, Math.addExact(FTYP.length, mediaSize));
            mediaSize = Math.addExact(mediaSize, chunk.length);
        }
        int movieSize = 8 + mvhd.size();
        for (Box track : tracks) movieSize = Math.addExact(movieSize, track.size());
        // 只输出有引用的音视频块，跳过 Apple 数据轨与顶层元数据。
        MessageDigest written = sha256();
        try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(Files.newOutputStream(target), written))) {
            out.write(FTYP);
            out.writeInt(mediaSize);
            out.write(ascii("mdat"));
            for (Chunk chunk : chunks) {
                check.run();
                out.write(data, chunk.source, chunk.length);
            }
            out.writeInt(movieSize);
            out.write(ascii("moov"));
            out.write(data, mvhd.start, mvhd.size());
            for (Box track : tracks) out.write(data, track.start, track.size());
        }
        // 用写出时的摘要核对读回数据，覆盖源样本、时间表和重算后的块偏移。
        MessageDigest readBack = sha256();
        try (var input = Files.newInputStream(target)) {
            byte[] block = new byte[65536];
            for (int count; (count = input.read(block)) >= 0;) {
                check.run();
                readBack.update(block, 0, count);
            }
        }
        if (!MessageDigest.isEqual(written.digest(), readBack.digest()))
            throw new IOException("源样本封装文件读回不一致");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    // 检查轨道只引用容器自身，拒绝指向外部文件的 MOV／MP4。
    private static void requireSelfContained(ByteBuffer data, Box minf) throws IOException {
        Box reference = one(data, one(data, minf, "dinf"), "dref");
        require(reference.end - reference.body >= 8 && data.getInt(reference.body + 4) == 1,
                "轨道的数据引用不受支持");
        Box entries = new Box(reference.start, reference.body + 8, reference.end, "dref");
        List<Box> refs = boxes(data, entries);
        require(refs.size() == 1, "轨道的数据引用不完整");
        Box ref = refs.get(0);
        require((ref.type.equals("url ") || ref.type.equals("alis"))
                && ref.end - ref.body == 4 && data.getInt(ref.body) == 1,
                "轨道引用了容器外部数据");
    }

    private static List<Chunk> readChunks(ByteBuffer data, Box table, Box media, String handler) throws IOException {
        Box descriptions = one(data, table, "stsd");
        require(descriptions.end - descriptions.body >= 24 && data.getInt(descriptions.body + 4) == 1
                && data.getInt(descriptions.body + 8) == descriptions.end - descriptions.body - 8
                && data.getShort(descriptions.body + 22) == 1, "需要单一样本描述及内部引用");
        String codec = type(data, descriptions.body + 12);
        boolean supported = switch (handler) {
            case "vide" -> codec.equals("hvc1") || codec.equals("avc1");
            case "soun" -> codec.equals("mp4a");
            case "meta" -> codec.equals("mebx");
            default -> false;
        };
        require(supported, "源编码不在验证范围");
        Box decodeTimes = one(data, table, "stts");
        Box offsets = one(data, table, "stco");
        Box sizes = one(data, table, "stsz");
        Box layout = one(data, table, "stsc");
        int chunkCount = count(data, offsets, 4);
        int layoutCount = count(data, layout, 12);
        require(sizes.end - sizes.body >= 12, "stsz 缺少字段");
        int sampleSize = data.getInt(sizes.body + 4);
        int sampleCount = data.getInt(sizes.body + 8);
        require(sampleSize >= 0 && sampleCount > 0 && sampleCount <= 10000, "样本数量或大小超范围");
        require(sizes.end - sizes.body == 12 + (sampleSize == 0 ? 4 * sampleCount : 0), "stsz 表长不一致");
        validateTimeTable(data, decodeTimes, sampleCount);
        for (Box box : boxes(data, table)) {
            if (box.type.equals("ctts")) validateTimeTable(data, box, sampleCount);
        }
        int[] firstChunks = new int[layoutCount];
        int[] samplesPerChunk = new int[layoutCount];
        for (int i = 0; i < layoutCount; i++) {
            int entry = layout.body + 8 + 12 * i;
            firstChunks[i] = data.getInt(entry);
            samplesPerChunk[i] = data.getInt(entry + 4);
            require(firstChunks[i] >= 1 && firstChunks[i] <= chunkCount
                    && (i == 0 ? firstChunks[i] == 1 : firstChunks[i] > firstChunks[i - 1])
                    && samplesPerChunk[i] > 0 && data.getInt(entry + 8) == 1, "stsc 布局不受支持");
        }
        List<Chunk> chunks = new ArrayList<>();
        int run = 0;
        int sample = 0;
        for (int chunk = 1; chunk <= chunkCount; chunk++) {
            if (run + 1 < layoutCount && firstChunks[run + 1] == chunk) run++;
            int count = samplesPerChunk[run];
            require(count <= sampleCount - sample, "块内样本数量超界");
            int length = 0;
            for (int i = 0; i < count; i++, sample++) {
                int size = sampleSize != 0 ? sampleSize : data.getInt(sizes.body + 12 + 4 * sample);
                require(size > 0, "样本为空");
                length = Math.addExact(length, size);
            }
            int entry = offsets.body + 8 + 4 * (chunk - 1);
            int offset = data.getInt(entry);
            require(offset >= media.body && offset <= media.end && length <= media.end - offset, "源样本越过 mdat");
            chunks.add(new Chunk(offset, length, entry));
        }
        require(sample == sampleCount, "stsc 与 stsz 样本数不一致");
        return chunks;
    }

    private static void validateTimeTable(ByteBuffer data, Box table, int sampleCount) throws IOException {
        int runs = count(data, table, 8);
        int remaining = sampleCount;
        for (int i = 0; i < runs; i++) {
            int samples = data.getInt(table.body + 8 + 8 * i);
            require(samples > 0 && samples <= remaining, "时序表样本数越界");
            remaining -= samples;
        }
        require(remaining == 0, "时序表与样本数不一致");
    }

    private static int count(ByteBuffer data, Box box, int width) throws IOException {
        require(box.end - box.body >= 8, box.type + " 缺少字段");
        int n = data.getInt(box.body + 4);
        require(n > 0 && n <= 10000 && box.end - box.body == 8 + width * n, box.type + " 表长不一致");
        return n;
    }

    private static Box one(ByteBuffer data, Box parent, String name) throws IOException {
        Box result = null;
        for (Box box : boxes(data, parent)) {
            if (!box.type.equals(name)) continue;
            require(result == null, "重复容器字段：" + name);
            result = box;
        }
        require(result != null, "缺少容器字段：" + name);
        return result;
    }

    private static List<Box> boxes(ByteBuffer data, Box parent) throws IOException {
        List<Box> result = new ArrayList<>();
        for (int pos = parent.body; pos < parent.end;) {
            require(parent.end - pos >= 8, "容器字段头被截断");
            int size = data.getInt(pos);
            require(size >= 8 && size <= parent.end - pos, "容器字段长度不受支持");
            result.add(new Box(pos, pos + 8, pos + size, type(data, pos + 4)));
            pos += size;
        }
        return result;
    }

    private static String type(ByteBuffer data, int start) {
        return new String(data.array(), start, 4, StandardCharsets.US_ASCII);
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }

    private static void require(boolean valid, String reason) throws IOException {
        if (!valid) throw new UnsupportedLayout(reason);
    }

    private static final class MovieLayout {
        final Box movieHeader;
        final List<Box> tracks;
        final List<Chunk> chunks;

        MovieLayout(Box movieHeader, List<Box> tracks, List<Chunk> chunks) {
            this.movieHeader = movieHeader;
            this.tracks = tracks;
            this.chunks = chunks;
        }
    }

    private static final class Box {
        final int start, body, end;
        final String type;
        Box(int start, int body, int end, String type) {
            this.start = start;
            this.body = body;
            this.end = end;
            this.type = type;
        }
        int size() { return end - start; }
    }

    private static final class Chunk {
        final int source, length, tableOffset;
        Chunk(int source, int length, int tableOffset) {
            this.source = source;
            this.length = length;
            this.tableOffset = tableOffset;
        }
    }

}
