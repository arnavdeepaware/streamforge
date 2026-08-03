type WizardStepsProps = {
  currentStep: number;
  onSelect: (step: number) => void;
  steps: readonly string[];
};

export function WizardSteps({
  currentStep,
  onSelect,
  steps,
}: WizardStepsProps) {
  return (
    <ol aria-label="Pipeline creation steps" className="wizard-steps">
      {steps.map((step, index) => (
        <li key={step}>
          <button
            aria-current={index === currentStep ? 'step' : undefined}
            className={
              index === currentStep
                ? 'wizard-step wizard-step--current'
                : 'wizard-step'
            }
            onClick={() => onSelect(index)}
            type="button"
          >
            <span>{index + 1}</span>
            {step}
          </button>
        </li>
      ))}
    </ol>
  );
}

type TextFieldProps = {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  inputMode?: 'numeric';
};

export function TextField({
  label,
  name,
  value,
  onChange,
  error,
  inputMode,
}: TextFieldProps) {
  const errorId = `${name}-error`;
  return (
    <div className="text-field">
      <label htmlFor={name}>{label}</label>
      <input
        aria-describedby={error ? errorId : undefined}
        aria-invalid={error ? true : undefined}
        id={name}
        inputMode={inputMode}
        onChange={(event) => onChange(event.target.value)}
        value={value}
      />
      <FieldError id={errorId} message={error} />
    </div>
  );
}

export function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span className="field-error" id={id} role="alert">
      {message}
    </span>
  ) : null;
}
