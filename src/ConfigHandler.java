import com.google.gson.Gson;
import models.Config;
import models.ErrorType;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ConfigHandler {

  Config loadConfig() {
    try {
      List<String> res = Files.readAllLines(Paths.get("config.json"));
      String content = String.join("\n", res);
      Gson gson = new Gson();
      return gson.fromJson(content, Config.class);
    } catch (Exception e) {
      // Crash out of app - Failed to load config file
      ErrorHandler.error(ErrorType.configError, e, "Failed to load config.");
      System.exit(1);
      return null;
    }
  }
}
