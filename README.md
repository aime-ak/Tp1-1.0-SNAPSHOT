# tp2aime25-26

Simple Java 21 Maven project using the Gemini HTTP API, Spring AI prompt classes, and LangChain4j embeddings.

## Main classes

- `ht.aime.Test1`
- `ht.aime.Test2` (tokens + cout USD)
- `ht.aime.Test3` (traducteur avec `PromptTemplate` et `Prompt`)
- `ht.aime.Test4` (embeddings + similarite cosinus avec LangChain4j)
- `ht.aime.Test5` (RAG simple avec `AiServices` et `infos.txt`)
- `ht.aime.Test6` (RAG conversationnel sur PDF avec decoupage en morceaux)
- `ht.aime.Test7` (conversation avec outil meteo Open-Meteo)
- `ht.aime.MeteoTool` (outil meteo + geolocalisation)

## Requirements

- Java 21
- Maven 3.9+
- Environment variable `GEMINI_KEY`

If the Gemini API returns a transient quota or availability error (`429` or `503`), the app retries a few times and then shows a readable message instead of raw JSON or a Maven failure.

## Build and run (cmd.exe)

```cmd
cd /d c:\Users\hugue\IdeaProjects\tp2aime25-26
set GEMINI_KEY=your_api_key_here
mvn clean compile
java -cp target\classes ht.aime.Test1
```

## Test 2 (tokens + cout)

```cmd
cd /d c:\Users\hugue\IdeaProjects\tp2aime25-26
set GEMINI_KEY=your_api_key_here
mvn clean compile
java -cp target\classes ht.aime.Test2
```

## Test 3 (traducteur)

```cmd
cd /d c:\Users\hugue\IdeaProjects\tp2aime25-26
set GEMINI_KEY=your_api_key_here
mvn clean compile
java -cp target\classes ht.aime.Test3 "Je suis etudiant en informatique."
```

## Test 4 (embeddings)

```cmd
cd /d c:\Users\hugue\IdeaProjects\tp2aime25-26
set GEMINI_KEY=your_api_key_here
mvn clean compile
mvn -Dexec.mainClass=ht.aime.Test4 exec:java
```

## Test 5 (RAG)

Le fichier `infos.txt` a ete ajoute a la racine du projet et sert de base de connaissances pour le retrieval.
La sortie affiche maintenant aussi les passages recuperes depuis `infos.txt` dans la section `Contexte RAG`.

```cmd
cd /d c:\Users\hugue\IdeaProjects\tp2aime25-26
set GEMINI_KEY=your_api_key_here
mvn clean compile
mvn -Dexec.mainClass=ht.aime.Test5 exec:java
```

Pour poser une autre question, par exemple `Comment s'appelle le chat de Pierre ?` :

```cmd
mvn -Dexec.mainClass=ht.aime.Test5 "-Dexec.args=Comment se nomme le chat de Pierre ?" exec:java
```

## Test 6 (RAG conversationnel sur PDF)

Copiez le support de cours au format PDF a la racine du projet. `Test6` detecte le premier fichier `.pdf` trouve a la racine, le decoupe en morceaux, calcule leurs embeddings et ouvre ensuite une conversation avec l'assistant dans la meme session.

Sous PowerShell :

```powershell
$env:GEMINI_KEY="your_api_key_here"
mvn clean compile
mvn --% -Dexec.mainClass=ht.aime.Test6 exec:java
```

Exemples de questions :

- `Quel est l'objectif du cours "Agents conversationnels en Java avec LangChain4j" de Richard Grin ?`
- `Dans quel slide as-tu trouve cette information ?`
- `Fais-moi un quiz QCM sur le machine learning et indique les bonnes reponses.`

## Test 7 (outils + Open-Meteo)

`Test7` ouvre une conversation avec un assistant qui peut utiliser `MeteoTool` pour :

- recuperer les previsions de pluie a partir d'une latitude et d'une longitude ;
- retrouver les coordonnees d'une ville avec l'API de geocodage Open-Meteo.

Tester directement l'outil seul :

```powershell
mvn clean compile
mvn --% -Dexec.mainClass=ht.aime.MeteoTool "-Dexec.args=Paris" exec:java
```

Lancer la conversation avec outils :

```powershell
$env:GEMINI_KEY="your_api_key_here"
mvn clean compile
mvn --% -Dexec.mainClass=ht.aime.Test7 exec:java
```

Question unique en ligne de commande :

```powershell
mvn --% -Dexec.mainClass=ht.aime.Test7 "-Dexec.args=Je dois partir dans 2 jours a Paris. Est-ce que je dois mettre un parapluie dans mes valises ?" exec:java
```

Exemples de questions :

- `J'ai prevu d'aller aujourd'hui a la ville dont la latitude est 48.85 et la longitude est 2.35 pour un sejour de 3 jours. Est-ce que tu me conseilles de mettre un parapluie dans ma valise ?`
- `Finalement, je ne vais partir que demain. Est-ce que tu me conseilles de prendre un parapluie ?`
- `J'ai prevu d'aller aujourd'hui a Paris pour un sejour de 3 jours. Est-ce que tu me conseilles de mettre un parapluie dans ma valise ?`
- `Qui a ecrit Les Miserables ?`
