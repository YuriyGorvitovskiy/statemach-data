SELECT tc.constraint_name
     , tc.table_name
     , kcu.column_name
  FROM information_schema.table_constraints AS tc
  JOIN information_schema.key_column_usage AS kcu  ON  tc.constraint_name = kcu.constraint_name
                                                   AND tc.table_schema = kcu.table_schema
 WHERE tc.constraint_type = 'PRIMARY KEY'
   AND tc.table_schema = ?
 ORDER BY kcu.ordinal_position ASC
