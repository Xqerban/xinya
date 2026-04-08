import bcrypt

password = b"password"
hash_example = b"$2b$12$kApU8J3N.uzZYjMMieSci./o63ZV2O/kMM6MgjFJik6.xkC.qjlf6"
print(bcrypt.checkpw(password, hash_example))  # 输出 True