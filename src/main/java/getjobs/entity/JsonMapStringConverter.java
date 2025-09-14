package getjobs.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

@Converter
public class JsonMapStringConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try {
            if (attribute == null)
                return null;
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty())
                return Collections.emptyMap();
            return MAPPER.readValue(dbData, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
