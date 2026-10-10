SELECT 'CREATE DATABASE travel WITH ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'travel')
\gexec
