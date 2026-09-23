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
            className={`wizard-step${index === currentStep ? ' wizard-step--current' : ''}${index < currentStep ? ' wizard-step--complete' : ''}`}
            onClick={() => onSelect(index)}
            type="button"
          >
            <span>
              {index < currentStep ? (
                <>
                  <span className="visually-hidden">{index + 1}</span>
                  <Check aria-hidden="true" size={13} />
                </>
              ) : (
                index + 1
              )}
            </span>
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
  requirement?: 'required' | 'optional';
};

export function TextField({
  label,
  name,
  value,
  onChange,
  error,
  inputMode,
  requirement = 'required',
}: TextFieldProps) {
  const errorId = `${name}-error`;
  return (
    <div className="text-field">
      <label htmlFor={name}>
        {label}{' '}
        <span aria-hidden="true" className="field-requirement">
          {requirement}
        </span>
      </label>
      <input
        aria-label={label}
        aria-describedby={error ? errorId : undefined}
        aria-invalid={error ? true : undefined}
        id={name}
        inputMode={inputMode}
        onChange={(event) => onChange(event.target.value)}
        required={requirement === 'required'}
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
import { Check } from 'lucide-react';
