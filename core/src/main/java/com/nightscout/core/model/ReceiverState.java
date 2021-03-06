// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: /Users/klee/AndroidStudioProjects/Scout/core/src/main/java/com/nightscout/core/model/Download.proto
package com.nightscout.core.model;

import com.squareup.wire.Message;
import com.squareup.wire.ProtoField;

import static com.squareup.wire.Message.Datatype.ENUM;
import static com.squareup.wire.Message.Datatype.UINT64;
import static com.squareup.wire.Message.Label.REQUIRED;

public final class ReceiverState extends Message {

    public static final Long DEFAULT_TIMESTAMP_MS = 0L;
    public static final ReceiverStatus DEFAULT_EVENT = ReceiverStatus.RECEIVER_CONNECTED;

    @ProtoField(tag = 1, type = UINT64, label = REQUIRED)
    public final Long timestamp_ms;

    @ProtoField(tag = 2, type = ENUM)
    public final ReceiverStatus event;

    public ReceiverState(Long timestamp_ms, ReceiverStatus event) {
        this.timestamp_ms = timestamp_ms;
        this.event = event;
    }

    private ReceiverState(Builder builder) {
        this(builder.timestamp_ms, builder.event);
        setBuilder(builder);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof ReceiverState)) return false;
        ReceiverState o = (ReceiverState) other;
        return equals(timestamp_ms, o.timestamp_ms)
                && equals(event, o.event);
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if (result == 0) {
            result = timestamp_ms != null ? timestamp_ms.hashCode() : 0;
            result = result * 37 + (event != null ? event.hashCode() : 0);
            hashCode = result;
        }
        return result;
    }

    public static final class Builder extends Message.Builder<ReceiverState> {

        public Long timestamp_ms;
        public ReceiverStatus event;

        public Builder() {
        }

        public Builder(ReceiverState message) {
            super(message);
            if (message == null) return;
            this.timestamp_ms = message.timestamp_ms;
            this.event = message.event;
        }

        public Builder timestamp_ms(Long timestamp_ms) {
            this.timestamp_ms = timestamp_ms;
            return this;
        }

        public Builder event(ReceiverStatus event) {
            this.event = event;
            return this;
        }

        @Override
        public ReceiverState build() {
            checkRequiredFields();
            return new ReceiverState(this);
        }
    }
}
