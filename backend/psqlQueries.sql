sudo -u postgres psql
CREATE USER chess_app WITH PASSWORD 'password';
CREATE DATABASE chess_db OWNER chess_app;