package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaInfo(String category, String extension, @JsonProperty("isa_set") String isaSet) {
}
