package com.hamusuke.packetcap.network;

import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.compress.utils.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

public class WrittenBytesLoggingByteBuf extends FriendlyByteBuf {
    private static final int FIRST = -1;
    private int lastLineNum = FIRST;
    private int lastWriterIndex = 0;
    private String lastWrittenBy = "";
    private int packetId = -1;
    private int packetIdEndIndex = -1;
    private final List<WriteLog> logs = Lists.newArrayList();

    public WrittenBytesLoggingByteBuf(ByteBuf p_130051_) {
        super(p_130051_);
    }

    @Override
    public FriendlyByteBuf writeBoolean(boolean p_300653_) {
        this.onWrite("writeBoolean");
        return super.writeBoolean(p_300653_);
    }

    @Override
    public FriendlyByteBuf writeByte(int i) {
        this.onWrite("writeByte");
        return super.writeByte(i);
    }

    @Override
    public FriendlyByteBuf writeShort(int i) {
        this.onWrite("writeShort");
        return super.writeShort(i);
    }

    @Override
    public FriendlyByteBuf writeDouble(double p_301246_) {
        this.onWrite("writeDouble");
        return super.writeDouble(p_301246_);
    }

    @Override
    public FriendlyByteBuf writeInt(int i) {
        this.onWrite("writeInt");
        return super.writeInt(i);
    }

    @Override
    public int writeCharSequence(CharSequence charSequence, Charset charset) {
        this.onWrite("writeCharSequence");
        return super.writeCharSequence(charSequence, charset);
    }

    @Override
    public FriendlyByteBuf writeBytes(byte[] bytes) {
        this.onWrite("writeBytes");
        return super.writeBytes(bytes);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf byteBuf) {
        this.onWrite("writeBytes");
        return super.writeBytes(byteBuf);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuffer byteBuffer) {
        this.onWrite("writeBytes");
        return super.writeBytes(byteBuffer);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf byteBuf, int i) {
        this.onWrite("writeBytes");
        return super.writeBytes(byteBuf, i);
    }

    @Override
    public FriendlyByteBuf writeBytes(byte[] bytes, int i, int i1) {
        this.onWrite("writeBytes");
        return super.writeBytes(bytes, i, i1);
    }

    @Override
    public FriendlyByteBuf writeChar(int i) {
        this.onWrite("writeChar");
        return super.writeChar(i);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf byteBuf, int i, int i1) {
        this.onWrite("writeBytes");
        return super.writeBytes(byteBuf, i, i1);
    }

    @Override
    public FriendlyByteBuf writeFloat(float v) {
        this.onWrite("writeFloat");
        return super.writeFloat(v);
    }

    @Override
    public FriendlyByteBuf writeLong(long l) {
        this.onWrite("writeLong");
        return super.writeLong(l);
    }

    @Override
    public FriendlyByteBuf writeMedium(int i) {
        this.onWrite("writeMedium");
        return super.writeMedium(i);
    }

    @Override
    public int writeBytes(ScatteringByteChannel scatteringByteChannel, int i) throws IOException {
        this.onWrite("writeBytes");
        return super.writeBytes(scatteringByteChannel, i);
    }

    @Override
    public int writeBytes(FileChannel fileChannel, long l, int i) throws IOException {
        this.onWrite("writeBytes");
        return super.writeBytes(fileChannel, l, i);
    }

    @Override
    public int writeBytes(InputStream inputStream, int i) throws IOException {
        this.onWrite("writeBytes");
        return super.writeBytes(inputStream, i);
    }

    @Override
    public FriendlyByteBuf writeZero(int i) {
        this.onWrite("writeZero");
        return super.writeZero(i);
    }

    @Override
    public FriendlyByteBuf writeShortLE(int i) {
        this.onWrite("writeShortLE");
        return super.writeShortLE(i);
    }

    @Override
    public ByteBuf writeDoubleLE(double value) {
        this.onWrite("writeDoubleLE");
        return super.writeDoubleLE(value);
    }

    @Override
    public ByteBuf writeFloatLE(float value) {
        this.onWrite("writeFloatLE");
        return super.writeFloatLE(value);
    }

    @Override
    public FriendlyByteBuf writeLongLE(long l) {
        this.onWrite("writeLongLE");
        return super.writeLongLE(l);
    }

    @Override
    public FriendlyByteBuf writeIntLE(int i) {
        this.onWrite("writeIntLE");
        return super.writeIntLE(i);
    }

    @Override
    public FriendlyByteBuf writeMediumLE(int i) {
        this.onWrite("writeMediumLE");
        return super.writeMediumLE(i);
    }

    private void addLog(WriteLog log) {
        this.logs.add(log);
    }

    public List<WriteLog> getLogs() {
        return ImmutableList.copyOf(this.logs);
    }

    public void assignPacketId(int packetId) {
        this.packetId = packetId;
        this.packetIdEndIndex = this.writerIndex() - 1;
    }

    public int getPacketId() {
        return this.packetId;
    }

    public int getPacketIdEndIndex() {
        return this.packetIdEndIndex;
    }

    private Optional<Integer> getNewLineNumIfLineChanged() {
        var st = new Throwable().getStackTrace();
        for (var e : st) {
            if (e.getClassName().startsWith("net.minecraft.network.protocol.")) { // Packet$write
                if (this.lastLineNum != e.getLineNumber()) {
                    return Optional.of(e.getLineNumber());
                }

                break;
            }
        }

        return Optional.empty();
    }

    private void onWrite(String writtenBy) {
        if (this.lastLineNum == FIRST) {
            this.getNewLineNumIfLineChanged().ifPresent(i -> this.push(i, writtenBy, this.writerIndex()));
            return;
        }

        this.getNewLineNumIfLineChanged().ifPresent(i -> {
            this.addLog(new WriteLog(this.lastWrittenBy, this.lastWriterIndex, this.writerIndex() - 1));
            this.push(i, writtenBy, this.writerIndex());
        });
    }

    private void push(int lineNum, String writtenBy, int writerIndex) {
        this.lastLineNum = lineNum;
        this.lastWriterIndex = writerIndex;
        this.lastWrittenBy = writtenBy;
    }

    public void onFinishWriting() {
        this.addLog(new WriteLog(this.lastWrittenBy, this.lastWriterIndex, this.writerIndex() - 1));
    }

    public record WriteLog(String writtenBy, int startIndex, int endIndex) {
    }
}
