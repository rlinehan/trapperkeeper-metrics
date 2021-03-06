(ns puppetlabs.trapperkeeper.services.metrics.jolokia
  "Clojure helpers for constructing and configuring Jolokia servlets."
  (:require [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [schema.core :as schema])
  (:import [org.jolokia.config ConfigKey]
           [org.jolokia.util LogHandler]
           [org.jolokia.http AgentServlet]))


(def config-mapping
  "Inspects the Jolokia ConfigKey Enum and generates a mapping that associates
  a Clojure keyword with each configuration parameter. The keyword used is the
  camel-cased identifier that would be used to configure a servlet via a
  web.xml file. For example, `ConfigKey/AGENT_ID` is associated with the
  keyword `:agentId`.

  For a complete list of configuration options, see:

    https://jolokia.org/reference/html/agents.html#agent-war-init-params"
  (->> (ConfigKey/values)
       (map (juxt
              #(-> % .getKeyValue keyword)
              identity))
       (into {})))

(schema/defschema JolokiaConfig
  "Schema for validating Clojure maps containing Jolokia configuration.

  Creates a map of optional keys which have string values using the
  config-mapping extracted from the ConfigKey enum."
  (->> (keys config-mapping)
       (map #(vector (schema/optional-key %) schema/Str))
       (into {})))

(def config-defaults
  "Default configuration values for Jolokia."
  {;; Without this, no debug-level messages are produced by the jolokia
   ;; namespace. Logback configuration still needs to be adjusted to
   ;; let these messages through.
   :debug "true"
   ;; Don't include backtraces in error results returned by the API.
   :allowErrorDetails "false"
   ;; Load access policy from: resources/jolokia-access.xml
   :policyLocation "classpath:/jolokia-access.xml"
   :mimeType "application/json"})


(defn create-servlet-config
  "Generate Jolokia AgentServlet configuration from a Clojure map"
  ([]
   (create-servlet-config {}))
  ([config]
   (->> config
        (merge config-defaults)
        ;; Validate here to ensure defaults are also valid.
        (schema/validate JolokiaConfig)
        walk/stringify-keys)))

(defn create-logger
  "Return an object that implements the Jolokia logging interface using the
  logger from clojure.tools.logging"
  []
  (reify
    LogHandler
    (debug [this message] (log/debug message))
    (info [this message] (log/info message))
    (error [this message throwable] (log/error throwable message))))

(defn create-servlet
  "Builds a Jolokia Servlet that uses Clojure logging."
  []
  (proxy [AgentServlet] []
    ;; NOTE: An alternative to this method override would be to use defrecord
    ;; to create a class that can be set as `:logHandlerClass` in the servlet
    ;; configuration. This requires AOT compilation for the namespace defining
    ;; the record so that Jolokia can find the resulting class.
    (createLogHandler [_ _]
      (create-logger))))
