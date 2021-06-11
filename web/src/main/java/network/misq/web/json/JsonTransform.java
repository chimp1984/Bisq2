package network.misq.web.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class JsonTransform {

    private final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    public String toJson(Map<String, Object> map) {
        return gson.toJson(map, map.getClass());
    }
}
