import pandas as pd
import xgboost as xgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report
import pickle

def main():
    dataset_path = "training_dataset.csv"
    model_output_path = "threat_model.json"
    
    print(f"Loading dataset from {dataset_path}...")
    try:
        df = pd.read_csv(dataset_path)
    except FileNotFoundError:
        print("Dataset not found! Run data_collector.py first.")
        return

    # We want the AI to learn to predict the 'final_malicious_label'
    # using ONLY the URL features, NOT the API responses (since we won't have APIs on the phone)
    
    feature_cols = [
        "url_length", "domain_length", "has_https", 
        "num_dots", "num_hyphens", "has_login_keyword", "has_admin_keyword"
    ]
    
    X = df[feature_cols]
    y = df["final_malicious_label"]

    # Split into 80% training data, 20% testing data
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    print("Training XGBoost AI Model (The Student)...")
    model = xgb.XGBClassifier(
        n_estimators=100, 
        max_depth=4, 
        learning_rate=0.1, 
        use_label_encoder=False,
        eval_metric='logloss'
    )
    
    model.fit(X_train, y_train)

    # Let's test the student!
    print("Testing the AI on unseen URLs...")
    predictions = model.predict(X_test)
    accuracy = accuracy_score(y_test, predictions)
    
    print(f"\nAI Accuracy: {accuracy * 100:.2f}%")
    print("\nDetailed Report:")
    print(classification_report(y_test, predictions, target_names=["Safe", "Malicious"]))

    # Save the model so we can convert it to TFLite later
    model.save_model(model_output_path)
    print(f"\nModel saved to {model_output_path}!")
    print("Next Step: Convert this JSON model to a .tflite file using TensorFlow and embed it into your Android app.")

if __name__ == "__main__":
    main()
