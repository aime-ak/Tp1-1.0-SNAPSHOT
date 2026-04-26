package ht.mbds.aime.tp1aime.bean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;

@Named("bb")
@SessionScoped
public class BackingBean implements Serializable {
    private String roleSysteme;
    private boolean roleSystemeChangeable = true;
    private String question;
    private String reponse;
    private String conversation;
    private String texteRequeteJson;
    private String texteReponseJson;
    private boolean debug;

    // Getters et setters
    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }

    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }
    public void setRoleSystemeChangeable(boolean roleSystemeChangeable) { this.roleSystemeChangeable = roleSystemeChangeable; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }

    public String getConversation() { return conversation; }
    public void setConversation(String conversation) { this.conversation = conversation; }

    public String getTexteRequeteJson() { return texteRequeteJson; }
    public void setTexteRequeteJson(String texteRequeteJson) { this.texteRequeteJson = texteRequeteJson; }

    public String getTexteReponseJson() { return texteReponseJson; }
    public void setTexteReponseJson(String texteReponseJson) { this.texteReponseJson = texteReponseJson; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public void toggleDebug() {
        this.setDebug(!isDebug());
    }

    // Actions
    public void envoyer() {
        // Ici tu peux simuler la réponse
        this.reponse = "Réponse automatique à: " + question;
        this.conversation = (conversation == null ? "" : conversation + "\n") + "Q: " + question + "\nR: " + reponse;
    }

    public void nouveauChat() {
        this.question = "";
        this.reponse = "";
        this.conversation = "";
        this.roleSystemeChangeable = true;
    }

    public String[] getRolesSysteme() {
        return new String[]{"assistant", "admin", "utilisateur"};
    }
}