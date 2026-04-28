package main

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"flag"
	"fmt"
	"net/http"
)

var privateKey *rsa.PrivateKey
var notaryName string

func main() {
	port := flag.Int("port", 4000, "Port für den Notar")
	name := flag.String("name", "Notar", "Name des Notars")
	flag.Parse()
	notaryName = *name

	var err error
	privateKey, err = rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		fmt.Printf("[%s] Fehler bei RSA-Generierung: %v\n", notaryName, err)
		return
	}

	http.HandleFunc("/sign",   handleSign)
	http.HandleFunc("/verify", handleVerify)
	http.HandleFunc("/pubkey", handlePubkey)

	fmt.Printf("HTP-%s aktiv auf Port %d\n", notaryName, *port)
	if err := http.ListenAndServe(fmt.Sprintf(":%d", *port), nil); err != nil {
		fmt.Printf("[%s] Server-Fehler: %v\n", notaryName, err)
	}
}

func corsHeaders(w http.ResponseWriter, r *http.Request) bool {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
	if r.Method == "OPTIONS" {
		w.WriteHeader(http.StatusOK)
		return true
	}
	return false
}

// /sign – empfängt base64url-Payload, signiert SHA256 davon
func handleSign(w http.ResponseWriter, r *http.Request) {
	if corsHeaders(w, r) { return }
	fmt.Printf("[%s] /sign Anfrage von %s\n", notaryName, r.RemoteAddr)

	var input struct {
		Payload string `json:"payload"` // base64url-kodiertes JSON
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		http.Error(w, "Bad Request", 400)
		return
	}

	// Payload dekodieren und Hash bilden
	payloadBytes, err := base64.URLEncoding.DecodeString(input.Payload)
	if err != nil {
		http.Error(w, "Bad Payload Encoding", 400)
		return
	}
	hash := sha256.Sum256(payloadBytes)

	// RSA-PKCS1v15 Signatur
	sig, err := rsa.SignPKCS1v15(rand.Reader, privateKey, crypto.SHA256, hash[:])
	if err != nil {
		http.Error(w, "Sign Error", 500)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"signature": base64.URLEncoding.EncodeToString(sig),
		"notary":    notaryName,
	})
}

// /verify – prüft ob eine Signatur gültig ist
func handleVerify(w http.ResponseWriter, r *http.Request) {
	if corsHeaders(w, r) { return }

	var input struct {
		Payload   string `json:"payload"`
		Signature string `json:"signature"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		http.Error(w, "Bad Request", 400)
		return
	}

	payloadBytes, err := base64.URLEncoding.DecodeString(input.Payload)
	if err != nil {
		respondVerify(w, false)
		return
	}
	sigBytes, err := base64.URLEncoding.DecodeString(input.Signature)
	if err != nil {
		respondVerify(w, false)
		return
	}

	hash := sha256.Sum256(payloadBytes)
	err = rsa.VerifyPKCS1v15(&privateKey.PublicKey, crypto.SHA256, hash[:], sigBytes)

	valid := err == nil
	fmt.Printf("[%s] /verify → valid=%v\n", notaryName, valid)
	respondVerify(w, valid)
}

func respondVerify(w http.ResponseWriter, valid bool) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"valid":  valid,
		"notary": notaryName,
	})
}

// /pubkey – liefert den öffentlichen Schlüssel als PEM
func handlePubkey(w http.ResponseWriter, r *http.Request) {
	if corsHeaders(w, r) { return }
	pubDER, _ := x509.MarshalPKIXPublicKey(&privateKey.PublicKey)
	pubPEM := pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: pubDER})
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"pubkey": string(pubPEM),
		"notary": notaryName,
	})
}
