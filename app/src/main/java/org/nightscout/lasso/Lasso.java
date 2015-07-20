package org.nightscout.lasso;

public class Lasso extends com.activeandroid.app.Application {
    private boolean serviceStarted = false;

    public boolean isServiceStarted() {
        return serviceStarted;
    }

    public void setServiceStarted(boolean serviceStarted) {
        this.serviceStarted = serviceStarted;
    }
}
