package sim;

import java.util.HashMap;
import java.util.Map;

public class HandlersHelper {

	private static Map<String, Integer> interactionClassMapping;
	
	static {
		interactionClassMapping = new HashMap<String, Integer>();
	}
	
	public static void addInteractionClassHandler(String interactionName, Integer handle) {
		interactionClassMapping.put(interactionName, handle);
	}
	
	public static Integer getInteractionHandleByName(String name) {
		if(interactionClassMapping.get(name) == null)
			return null;
		return interactionClassMapping.get(name).intValue();
	}
	
}
