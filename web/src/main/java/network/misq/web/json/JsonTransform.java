package network.misq.web.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Map;

public class JsonTransform {

    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public String toJson(Map<String, String> map) {
        return gson.toJson(map, map.getClass());
    }
}
