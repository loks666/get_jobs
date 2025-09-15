package getjobs.repository.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

@Converter
public class JsonListIntegerConverter implements AttributeConverter<List<Integer>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        try {
            if (attribute == null)
                return null;
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty())
                return Collections.emptyList();
            return MAPPER.readValue(dbData, new TypeReference<List<Integer>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
