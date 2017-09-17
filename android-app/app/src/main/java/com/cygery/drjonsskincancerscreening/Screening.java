package com.cygery.drjonsskincancerscreening;

import org.json.JSONException;
import org.json.JSONObject;

class Screening {
    protected String file;
    protected String path;
    protected Double prob;
    protected Long ts;

    public Screening(String file, JSONObject o) throws JSONException {
        this.file = file;
        path = o.getString("path");
        prob = o.getDouble("prob");
        ts = o.getLong("ts");
    }
}
