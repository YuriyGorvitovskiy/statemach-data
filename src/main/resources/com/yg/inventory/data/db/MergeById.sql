INSERT INTO ${0} (${1})
    VALUES (${2})
    ON CONFLICT (id) DO UPDATE SET ${3}
    RETURNING ${4}
