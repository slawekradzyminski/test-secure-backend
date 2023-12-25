import React from 'react';

interface PrimaryButtonProps {
    isLoading: boolean;
    text: string;
}

const PrimaryButton = (props: PrimaryButtonProps) => {
    return (
        <>
            <button className="btn btn-primary">
                {props.isLoading && <span className="spinner-border spinner-border-sm mr-1" />}
                {props.text}</button>
        </>
    );
};

export { PrimaryButton };