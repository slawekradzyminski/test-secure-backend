type ChangeHandler = (value: any) => void;

export const getHandleChange = (func: ChangeHandler) => {
  return (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const value = event.target.value;
    func(value);
  };
};