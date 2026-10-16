-- Only execute manually in an isolated test database.
CREATE TABLE example_items (id INTEGER PRIMARY KEY, name VARCHAR(100));
INSERT INTO example_items (id, name) VALUES (1, 'sample');
SELECT id, name FROM example_items WHERE id = 1;
