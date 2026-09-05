with open('app/src/main/res/values/strings.xml', 'r') as f:
    content = f.read()

content = content.replace("BB Companion", "BB Compagnon")

with open('app/src/main/res/values/strings.xml', 'w') as f:
    f.write(content)
