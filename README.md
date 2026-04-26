# Tp1-1.0-SNAPSHOT

Repository pour le TP1.

## Prérequis

- Java 17 ou supérieur
- Maven 3.6 ou supérieur

## Structure du projet

```
Tp1-1.0-SNAPSHOT/
├── pom.xml
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/tp1/
│   │           └── Main.java
│   └── test/
│       └── java/
│           └── com/tp1/
│               └── MainTest.java
└── README.md
```

## Compilation et exécution

### Compiler le projet

```bash
mvn compile
```

### Exécuter les tests

```bash
mvn test
```

### Créer le JAR exécutable

```bash
mvn package
```

### Exécuter le JAR

```bash
java -jar target/Tp1-1.0-SNAPSHOT.jar
```
