import bcrypt
hash = bcrypt.hashpw(b"password", bcrypt.gensalt())
print(hash.decode())