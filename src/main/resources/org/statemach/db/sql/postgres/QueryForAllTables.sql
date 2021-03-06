SELECT table_name, column_name, data_type, character_maximum_length
    FROM  information_schema.columns c
    WHERE table_schema = ?
    ORDER BY table_name ASC, ordinal_position ASC
