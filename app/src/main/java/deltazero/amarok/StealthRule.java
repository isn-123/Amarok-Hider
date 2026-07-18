package deltazero.amarok;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class StealthRule implements Serializable {
    public String id;
    public String name;
    public int startHour;
    public int startMinute;
    public int endHour;
    public int endMinute;
    public boolean[] weekdays; // Index 0 = Sunday, 1 = Monday, ..., 6 = Saturday
    public boolean enabled;

    public StealthRule() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.name = "";
        this.startHour = 8;
        this.startMinute = 0;
        this.endHour = 17;
        this.endMinute = 0;
        this.weekdays = new boolean[]{false, true, true, true, true, true, false}; // Mon-Fri
        this.enabled = true;
    }

    public JSONObject toJsonObject() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("startHour", startHour);
            json.put("startMinute", startMinute);
            json.put("endHour", endHour);
            json.put("endMinute", endMinute);
            json.put("enabled", enabled);
            
            JSONArray days = new JSONArray();
            for (boolean d : weekdays) {
                days.put(d);
            }
            json.put("weekdays", days);
            return json;
        } catch (JSONException e) {
            return null;
        }
    }

    public static StealthRule fromJsonObject(JSONObject json) {
        try {
            StealthRule rule = new StealthRule();
            rule.id = json.getString("id");
            rule.name = json.getString("name");
            rule.startHour = json.getInt("startHour");
            rule.startMinute = json.getInt("startMinute");
            rule.endHour = json.getInt("endHour");
            rule.endMinute = json.getInt("endMinute");
            rule.enabled = json.getBoolean("enabled");
            
            JSONArray days = json.getJSONArray("weekdays");
            rule.weekdays = new boolean[7];
            for (int i = 0; i < 7; i++) {
                rule.weekdays[i] = days.getBoolean(i);
            }
            return rule;
        } catch (JSONException e) {
            return null;
        }
    }
}
