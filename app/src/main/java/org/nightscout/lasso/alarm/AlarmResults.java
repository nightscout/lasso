package org.nightscout.lasso.alarm;

public class AlarmResults {
    public AlarmSeverity severity = AlarmSeverity.NONE;
    public String title;
    public String message = "";

    public AlarmResults(String title, AlarmSeverity severity, String message) {
        this.title = title;
        this.message = message;
        this.severity = severity;
    }

    public AlarmResults() {

    }

    public void setSeverityAtHighest(AlarmSeverity severity) {
        this.severity = (severity.ordinal() > this.severity.ordinal()) ? severity : this.severity;
    }

    public void appendMessage(String message) {
        if (message.equals("")) {
            return;
        }
        if (!this.message.equals("")) {
            this.message = this.message + "\n" + message;
        } else {
            this.message = message;
        }
    }

    public void appendMessages(String... messages) {
        for (String message : messages) {
            appendMessage(message);
        }
    }

    public void mergeAlarmResults(AlarmResults... results) {
        for (AlarmResults alarmResult : results) {
            appendMessage(alarmResult.message);
            setSeverityAtHighest(alarmResult.severity);
            if (title == null) {
                title = alarmResult.title;
            } else if (alarmResult.title != null) {
                title += "and " + alarmResult.title;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlarmResults that = (AlarmResults) o;

        if (severity != that.severity) return false;
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        return !(message != null ? !message.equals(that.message) : that.message != null);

    }

    @Override
    public int hashCode() {
        int result = severity != null ? severity.hashCode() : 0;
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }
}
