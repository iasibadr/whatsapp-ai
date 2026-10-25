package live.pharmaradio.whatsappai;

final class SetupFlow {
    enum Stage { KEY, INSTALL, DEVELOPER, WIRELESS, START, WRONG_MODE, AUTHORIZE, ACTIVATE, VERIFY, READY }

    static Stage next(boolean key, boolean helper, boolean diagnostic, boolean installed,
            boolean running, boolean shellMode, boolean authorized, boolean developer, boolean wireless) {
        if (!key) return Stage.KEY;
        if (helper) return diagnostic ? Stage.READY : Stage.VERIFY;
        if (running) {
            if (!shellMode) return Stage.WRONG_MODE;
            return authorized ? Stage.ACTIVATE : Stage.AUTHORIZE;
        }
        if (!installed) return Stage.INSTALL;
        if (!developer) return Stage.DEVELOPER;
        if (!wireless) return Stage.WIRELESS;
        return Stage.START;
    }

    static boolean automatic(Stage stage, boolean accepted, boolean attempted, boolean busy) {
        return accepted && !attempted && !busy && (stage == Stage.ACTIVATE || stage == Stage.VERIFY);
    }

    static String diagnosticMessage(String message) {
        if (message == null || message.isEmpty()) return "La vérification n’a pas abouti. Réessayez lorsque la connexion est disponible.";
        if (message.contains("credit_balance_exhausted") || message.contains("insufficient_quota")) {
            return "Le crédit ou quota API OpenAI est épuisé. Vérifiez la facturation du compte associé à votre clé. L’abonnement ChatGPT ne recharge pas ce crédit.";
        }
        return message;
    }
}
