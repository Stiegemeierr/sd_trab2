JAVAC = javac
JAVA = java
JAVADOC = javadoc
SRC_DIR = src
BIN_DIR = bin
DOC_DIR = doc
ENCODING = UTF-8

all: compile

compile:
	$(JAVAC) -encoding $(ENCODING) -d $(BIN_DIR) -sourcepath $(SRC_DIR) src/CausalMulticast/*.java src/client/*.java src/*.java

run:
	$(JAVA) -cp $(BIN_DIR) ClientApp $(IP) $(PORT)

run1:
	$(JAVA) -cp $(BIN_DIR) ClientApp 127.0.0.1 5001

run2:
	$(JAVA) -cp $(BIN_DIR) ClientApp 127.0.0.1 5002

run3:
	$(JAVA) -cp $(BIN_DIR) ClientApp 127.0.0.1 5003

javadoc:
	$(JAVADOC) -encoding $(ENCODING) -d $(DOC_DIR) -sourcepath $(SRC_DIR) -subpackages CausalMulticast

clean:
	rm -rf $(BIN_DIR)/* $(DOC_DIR)/*

test1: compile
	$(JAVA) -cp $(BIN_DIR) TestModulo1

test2: compile
	$(JAVA) -cp $(BIN_DIR) TestModulo2

test3: compile
	$(JAVA) -cp $(BIN_DIR) TestModulo3
