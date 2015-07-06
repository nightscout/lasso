package org.nightscout.lasso.alarm;

import net.tribe7.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;

public class AlarmResults {
    protected AlarmSeverity severity = AlarmSeverity.NONE;
    protected String title;
    protected List<String> messages = new ArrayList<>();

    public AlarmResults(String title, AlarmSeverity severity, String message) {
        this.title = title;
        appendMessage(message);
        this.severity = severity;
    }

    public AlarmResults() {

    }

    public void setSeverityAtHighest(AlarmSeverity severity) {
        this.severity = (severity.ordinal() > this.severity.ordinal()) ? severity : this.severity;
    }

    public void appendMessage(String message) {
        if (message == null || this.messages.contains(message) | message.equals("")){
            return;
        }
        this.messages.add(message);
    }

    public void appendMessage(List<String> messages) {
        for (String message: messages) {
            appendMessage(message);
        }
    }

    public String getMessage(){
        return Joiner.on('\n').join(this.messages);
    }

    public void addMessage(String message) {
        if (message == null || this.messages.contains(message) | message.equals("")){
            return;
        }
        this.messages.add(0, message);
    }

    public void appendMessages(String... messages) {
        for (String message : messages) {
            appendMessage(message);
        }
    }

    public void mergeAlarmResults(AlarmResults... results) {
        for (AlarmResults alarmResult : results) {
            appendMessage(alarmResult.messages);
            if (alarmResult.getSeverity().ordinal() > severity.ordinal()) {
                title = alarmResult.title;
            }
            setSeverityAtHighest(alarmResult.severity);
        }
    }

    public AlarmSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlarmResults that = (AlarmResults) o;

        if (severity != that.severity) return false;
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        return !(messages != null ? !messages.equals(that.messages) : that.messages != null);

    }

    @Override
    public int hashCode() {
        int result = severity != null ? severity.hashCode() : 0;
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (messages != null ? messages.hashCode() : 0);
        return result;
    }
}
